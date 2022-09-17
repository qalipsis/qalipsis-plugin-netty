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

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.socket.DatagramPacket
import io.netty.handler.codec.http.HttpContent
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.netty.monitoring.MonitoringCollector
import java.util.concurrent.atomic.AtomicInteger

/**
 * Channel handler to record the channel activity.
 *
 * @author Eric Jessé
 */
internal open class ChannelMonitoringHandler(
    private val monitoringCollector: MonitoringCollector
) : ChannelDuplexHandler() {

    /**
     * Phase of the discussion.
     */
    private val exchangePhase = AtomicInteger(INIT_PHASE)

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        log.trace { "Channel ${ctx.channel()}: removed handler" }
        super.handlerRemoved(ctx)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        log.trace { "Channel ${ctx.channel()}: ${if (exchangePhase.get() == RECEIVING_PHASE) "continuing reception" else "receiving data"}" }
        if (exchangePhase.compareAndSet(DATA_SENT_PHASE, RECEIVING_PHASE)) {
            log.trace { "Received first message after sending: $msg" }
            monitoringCollector.recordReceivingData()
        } else {
            log.trace { "Received message while still in exchange phase ${exchangePhase.get()}: $msg" }
        }

        if (exchangePhase.get() >= RECEIVING_PHASE) {
            val messageSize = getMessageSize(msg)
            if (messageSize > 0) {
                log.trace { "Counting received $messageSize bytes" }
                monitoringCollector.countReceivedData(messageSize)
            }
        }
        ctx.fireChannelRead(msg)
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        log.trace { "Channel ${ctx.channel()}: read complete" }
        if (exchangePhase.get() >= RECEIVING_PHASE) {
            log.trace { "Reception is complete" }
            monitoringCollector.recordReceptionComplete()
        }
        ctx.fireChannelReadComplete()
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        val size = getMessageSize(msg)
        if (size > 0) {
            // Exchange state change and event logging are only considered when bytes are sent on the wire.
            log.trace { "Channel ${ctx.channel()}: writing $size bytes" }
            monitoringCollector.recordSendingData(size)

            promise.addListener { result ->
                if (exchangePhase.compareAndSet(INIT_PHASE, DATA_SENT_PHASE)) {
                    log.trace { "Transition to the exchange phase DATA_SENT" }
                } else {
                    log.trace { "Staying in the exchange phase ${exchangePhase.get()} after write" }
                }
                if (result.isSuccess) {
                    log.trace { "Channel ${ctx.channel()}: writing $size bytes succeeded" }
                    monitoringCollector.recordSentDataSuccess(size)
                } else {
                    val cause = result.cause()
                    log.trace { "Channel ${ctx.channel()}: writing $size bytes failed, $cause" }
                    monitoringCollector.recordSentDataFailure(cause)
                }
            }
        }
        ctx.write(msg, promise)
    }

    private fun getMessageSize(msg: Any): Int {
        return when (msg) {
            is ByteBuf -> msg.readableBytes()
            is DatagramPacket -> msg.content().readableBytes()
            is ByteArray -> msg.size
            is HttpContent -> msg.content().readableBytes()
            else -> {
                log.warn { "Unsupported type to extract size: ${msg::class}" }
                0
            }
        }
    }

    private companion object {

        /**
         * Phase of the exchange with the server, when no user data was sent (still in the initialization phase).
         */
        const val INIT_PHASE = 0

        /**
         * Phase of the exchange with the server, when at least a bunch of user data was sent.
         * This phase can only follow [INIT_PHASE].
         */
        const val DATA_SENT_PHASE = 1

        /**
         * Phase of the exchange with the server, when at least a bunch of user data was received.
         * This phase can only follow [DATA_SENT_PHASE].
         */
        const val RECEIVING_PHASE = 2

        @JvmStatic
        val log = logger()
    }

}
