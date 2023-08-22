/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.plugins.netty.socket

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.ChannelPipeline
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.io.Closeable
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.asSuspended
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlin.coroutines.CoroutineContext

/**
 * Parent client of socket-based Netty clients.
 *
 * @property remainingUsages number of usages to client is expected to be performed
 *
 * @author Eric Jessé
 */
internal abstract class SocketClient<CONN : SocketClientConfiguration, REQ : Any, RES : Any, SELF : SocketClient<CONN, REQ, RES, SELF>>(
    private var remainingUsages: Long,
    private val ioCoroutineContext: CoroutineContext,
    private val onClose: SELF.() -> Unit = {}
) : Closeable {

    private var open = false

    private lateinit var channelFuture: ChannelFuture

    protected lateinit var readTimeout: Duration

    private lateinit var shutdownTimeout: Duration

    /**
     * Unique identifier of the remote peer to which the client is connected.
     */
    private lateinit var remotePeerIdentifier: RemotePeerIdentifier

    val peerIdentifier: RemotePeerIdentifier
        get() = remotePeerIdentifier

    /**
     * Open netty [Channel].
     */
    protected val channel: Channel
        get() = channelFuture.channel()

    /**
     * Returns `true if the client is open and connected, false otherwise.
     */
    open val isOpen: Boolean
        get() = open && channelFuture.channel().isOpen

    /**
     * Returns `true if all the planned usages of the client were performed, `false` otherwise.
     */
    fun isExhausted() = remainingUsages <= 0

    abstract suspend fun open(
        clientConfiguration: CONN,
        workerGroup: EventLoopGroup,
        monitoringCollector: SocketMonitoringCollector
    )

    suspend fun open(
        config: CONN, workerGroup: EventLoopGroup,
        monitoringCollector: SocketMonitoringCollector,
        initializer: ChannelInitializer<SocketChannel>,
        connectionReadyLatch: SuspendedCountLatch
    ) {
        readTimeout = config.readTimeout
        shutdownTimeout = config.shutdownTimeout
        remotePeerIdentifier = RemotePeerIdentifier(config.inetAddress, config.port)

        val bootstrap = Bootstrap().channel(NativeTransportUtils.socketChannelClass).handler(initializer)
        with(bootstrap) {
            group(workerGroup)
            remoteAddress(InetSocketAddress(config.inetAddress, config.port))
            option(ChannelOption.TCP_NODELAY, config.noDelay)
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.connectTimeout.toMillis().toInt())
            option(ChannelOption.SO_RCVBUF, config.receiveBufferSize)
            option(ChannelOption.SO_SNDBUF, config.sendBufferSize)
            option(ChannelOption.SO_KEEPALIVE, config.keepConnectionAlive)

            config.nettyChannelOptions.forEach { (option, value) ->
                @Suppress("UNCHECKED_CAST")
                option(option as ChannelOption<Any>, value)
            }
        }

        channelFuture = bootstrap.connect().addListener {
            log.trace { "Connection to $remotePeerIdentifier was successfully established" }
            connectionReadyLatch.blockingDecrement()
        }
        val timeout = config.connectTimeout.toMillis()
        if (!connectionReadyLatch.await(timeout)) {
            close()
            throw monitoringCollector.cause
                ?: TimeoutException(
                    "The client ${
                        channelFuture.channel().localAddress()
                    } could not connect within ${config.connectTimeout.toMillis()} ms"
                )
        }
        monitoringCollector.cause?.let {
            if (channelFuture.isDone) {
                close()
            }
            throw it
        }
        open = true
    }

    /**
     * Sends [request] to the remote peer.
     */
    abstract suspend fun <I> execute(
        stepContext: StepContext<I, *>,
        request: REQ,
        monitoringCollector: StepContextBasedSocketMonitoringCollector
    ): RES

    protected open suspend fun <I, INTERNAL_REQ : Any, INTERNAL_RES> internalExecute(
        stepContext: StepContext<I, *>,
        request: INTERNAL_REQ,
        monitoringCollector: StepContextBasedSocketMonitoringCollector,
        responseSlot: ImmutableSlot<Result<INTERNAL_RES>>
    ): INTERNAL_RES {
        remainingUsages--
        try {
            runCatchingTimeoutCancellationException("Confirmation acknowledgment reached the timeout $readTimeout") {
                write(request)
            }
        } catch (e: Exception) {
            monitoringCollector.recordSentDataFailure(e)
            throw e
        }

        return try {
            val response = runCatchingTimeoutCancellationException("Read timeout $readTimeout was reached ") {
                responseSlot.get(readTimeout)
            }.getOrThrow()
            log.trace { "Returning response $response" }
            response
        } catch (e: Exception) {
            log.trace(e) { e.message }
            monitoringCollector.recordReceivingDataFailure(e)
            throw e
        }
    }

    protected open suspend fun write(request: Any) {
        channel.writeAndFlush(request).asSuspended().get(readTimeout)
    }

    private suspend fun <T> runCatchingTimeoutCancellationException(
        timeoutMessage: String,
        block: suspend () -> T
    ): T {
        return try {
            withContext(ioCoroutineContext) {
                block()
            }
        } catch (e: TimeoutCancellationException) {
            throw TimeoutException(timeoutMessage)
        }
    }

    protected fun removeHandler(pipeline: ChannelPipeline, handlerName: String) {
        pipeline.context(handlerName)?.takeUnless { it.isRemoved }?.let {
            try {
                it.pipeline().remove(it.name())
            } catch (e: NoSuchElementException) {
                // Ignore in the case the element is no longer in the pipeline.
            }
        }
    }

    override suspend fun close() {
        if (open) {
            open = false
            try {
                // FIXME the promise emitted by channel.close() is never completed.
                // One option is to add a handler as head that completes the promise on close.
                //  channel.close().asSuspended().get(shutdownTimeout)
                channel.close()
                log.trace { "The channel is closed" }
            } finally {
                try {
                    @Suppress("UNCHECKED_CAST")
                    (this as SELF).onClose()
                } catch (e: Exception) {
                    log.warn(e) { e.message }
                }
            }
        }
    }

    /**
     * Unique identifier for a connection, in order to identify a peer used several times.
     */
    data class RemotePeerIdentifier(
        val address: InetAddress,
        val port: Int
    ) {
        companion object {

            @JvmStatic
            fun of(uri: String): RemotePeerIdentifier {
                val parsedUri = URI(uri)
                return RemotePeerIdentifier(InetAddress.getByName(parsedUri.host), parsedUri.port)
            }

        }
    }

    companion object {

        @JvmStatic
        private val log = logger()

    }
}
