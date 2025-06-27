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
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.ssl.SslHandshakeCompletionEvent
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.plugins.netty.monitoring.MonitoringCollector
import java.time.Duration

/**
 * Channel handler to monitor the TLS handshake.
 *
 * All the credits go to the project https://github.com/reactor/reactor-netty under the Apache License 2.0.
 *
 * @author Eric Jessé
 */
internal class TlsMonitoringHandler(
    private val monitoringCollector: MonitoringCollector,
    private val connectionReadyLatch: SuspendedCountLatch
) : ChannelInboundHandlerAdapter() {

    init {
        connectionReadyLatch.blockingIncrement()
    }

    private var tlsHandshakeTimeStart: Long? = null

    private var handshakeDone: Boolean = false

    override fun channelActive(ctx: ChannelHandlerContext) {
        log.trace { "Channel ${ctx.channel()}: starting to read" }
        ctx.read()
    }

    override fun channelRegistered(ctx: ChannelHandlerContext) {
        tlsHandshakeTimeStart = System.nanoTime()
        super.channelRegistered(ctx)
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        log.trace { "Channel ${ctx.channel()}: read complete, ${if (handshakeDone) "handshake done" else "handshake pending"}" }
        if (!handshakeDone) {
            // Continue consuming.
            ctx.read()
        }
        super.channelReadComplete(ctx)
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        log.trace { "Channel ${ctx.channel()}: user event $evt" }
        if (evt is SslHandshakeCompletionEvent) {
            val now = System.nanoTime()
            handshakeDone = true

            tlsHandshakeTimeStart?.let { start ->
                if (evt.isSuccess) {
                    log.trace { "Channel ${ctx.channel()}: handshake complete with success" }
                    monitoringCollector.recordTlsHandshakeSuccess(Duration.ofNanos(now - start))
                    ctx.fireChannelActive()
                } else {
                    val cause = evt.cause()
                    log.trace { "Channel ${ctx.channel()}: handshake failed, $cause" }
                    monitoringCollector.recordTlsHandshakeFailure(Duration.ofNanos(now - start), cause)
                    ctx.fireExceptionCaught(cause)
                }
            }
            connectionReadyLatch.blockingDecrement()
            // The handler is no longer required.
            ctx.pipeline().remove(this)
        }
        super.userEventTriggered(ctx, evt)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
