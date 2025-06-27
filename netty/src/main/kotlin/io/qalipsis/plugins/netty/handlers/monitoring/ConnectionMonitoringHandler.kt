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

package io.qalipsis.plugins.netty.handlers.monitoring

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.plugins.netty.monitoring.MonitoringCollector
import java.net.SocketAddress
import java.time.Duration

/**
 * Channel handler to record the connection meters and events.
 *
 * @author Eric Jessé
 */
internal class ConnectionMonitoringHandler(
    private val monitoringCollector: MonitoringCollector,
    private val connectionReadyLatch: SuspendedCountLatch
) : ChannelOutboundHandlerAdapter() {

    init {
        connectionReadyLatch.blockingIncrement()
    }

    private var connectionStart: Long? = null

    override fun connect(
        ctx: ChannelHandlerContext,
        remoteAddress: SocketAddress,
        localAddress: SocketAddress?,
        promise: ChannelPromise
    ) {
        log.trace { "Channel ${ctx.channel()}: connecting to $remoteAddress" }
        connectionStart = System.nanoTime()
        monitoringCollector.recordConnecting()
        promise.addListener { future ->
            val now = System.nanoTime()
            if (future.isSuccess) {
                log.trace { "Channel ${ctx.channel()}: connected" }
                monitoringCollector.recordConnected(Duration.ofNanos(now - connectionStart!!))
            } else {
                val cause = future.cause()
                log.debug { "Channel ${ctx.channel()}: connection failed, $cause" }
                monitoringCollector.recordConnectionFailure(Duration.ofNanos(now - connectionStart!!), cause)
            }
            connectionReadyLatch.blockingDecrement()
        }
        ctx.pipeline().remove(this)
        super.connect(ctx, remoteAddress, localAddress, promise)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        val now = System.nanoTime()
        monitoringCollector.recordConnectionFailure(Duration.ofNanos(now - connectionStart!!), cause)
        connectionReadyLatch.blockingDecrement()
        ctx.pipeline().remove(this)
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
