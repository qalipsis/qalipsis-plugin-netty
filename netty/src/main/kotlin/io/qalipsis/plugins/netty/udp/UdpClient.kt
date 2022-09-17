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

package io.qalipsis.plugins.netty.udp

import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelOption
import io.netty.channel.ChannelPipeline
import io.netty.channel.EventLoopGroup
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.api.sync.Latch
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.asSuspended
import io.qalipsis.plugins.netty.configuration.ConnectionConfiguration
import io.qalipsis.plugins.netty.handlers.ByteArrayInboundHandler
import io.qalipsis.plugins.netty.handlers.monitoring.ChannelMonitoringHandler
import io.qalipsis.plugins.netty.monitoring.MonitoringCollector
import java.net.InetSocketAddress
import java.net.SocketException
import java.time.Duration

/**
 * Netty UDP client.
 *
 * @author Eric Jessé
 */
internal class UdpClient {

    private lateinit var channel: ChannelFuture

    private lateinit var readTimeout: Duration

    suspend fun open(
        config: ConnectionConfiguration,
        workerGroup: EventLoopGroup,
        monitoringCollector: UdpMonitoringCollector
    ) {
        readTimeout = config.readTimeout

        val bootstrap = Bootstrap().channel(NativeTransportUtils.datagramChannelClass)
            .handler(UdpChannelInitializer())

        with(bootstrap) {
            group(workerGroup)
            remoteAddress(InetSocketAddress(config.host, config.port))
            option(ChannelOption.SO_RCVBUF, config.receiveBufferSize)
            option(ChannelOption.SO_SNDBUF, config.sendBufferSize)

            config.nettyChannelOptions.forEach { (option, value) ->
                @Suppress("UNCHECKED_CAST")
                option(option as ChannelOption<Any>, value)
            }
        }

        val channelLatch = Latch(true)
        channel = bootstrap.connect().addListener { channelLatch.cancel() }
        channelLatch.await()
        monitoringCollector.cause?.let { throw it }
        log.trace { "UDP client is now open" }
    }

    suspend fun <I> execute(
        stepContext: StepContext<I, *>,
        request: ByteArray,
        monitoringCollector: MonitoringCollector
    ): ByteArray {
        val responseSlot = ImmutableSlot<Result<ByteArray>>()
        val pipeline = channel.channel().pipeline()
        pipeline.addLast(CHANNEL_MONITORING_HANDLER, ChannelMonitoringHandler(monitoringCollector))
        pipeline.addLast(INBOUND_HANDLER, ByteArrayInboundHandler(responseSlot))
        try {
            channel.channel().writeAndFlush(request).asSuspended().get(readTimeout)
            return responseSlot.get(readTimeout).getOrThrow().also {
                log.trace { "Received ${it.size} bytes for the context $stepContext" }
            }
        } catch (e: SocketException) {
            monitoringCollector.recordSentDataFailure(e)
            throw e
        } finally {
            removeHandler(pipeline, CHANNEL_MONITORING_HANDLER)
            removeHandler(pipeline, INBOUND_HANDLER)
        }
    }

    private fun removeHandler(pipeline: ChannelPipeline, handlerName: String) {
        pipeline.context(handlerName)?.takeUnless { it.isRemoved }?.let {
            try {
                it.pipeline().remove(it.name())
            } catch (e: NoSuchElementException) {
                // Ignore in the case the element is no longer in the pipeline.
            }
        }
    }

    private companion object {

        const val CHANNEL_MONITORING_HANDLER = "udp.channel-monitoring"

        const val INBOUND_HANDLER = "udp.inbound-handler"

        @JvmStatic
        val log = logger()
    }

}
