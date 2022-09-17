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
