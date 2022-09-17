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
