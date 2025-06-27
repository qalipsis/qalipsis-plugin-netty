/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
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
