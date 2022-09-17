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

package io.qalipsis.plugins.netty.handlers

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.ImmutableSlot

/**
 * Handler for responses as [ByteArray]s, suitable for low-level clients (TCP, UDP).
 *
 * @author Eric Jessé
 */
internal class ByteArrayInboundHandler(
    private val response: ImmutableSlot<Result<ByteArray>>
) : SimpleChannelInboundHandler<ByteArray>() {

    private var startedRead = false

    private var received = ByteArray(0)

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteArray?) {
        startedRead = true
        if (msg != null) {
            log.trace { "Reading data" }
            received += msg
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        if (startedRead) {
            log.trace { "Read is complete" }
            response.offer(Result.success(received))
        } else {
            // The read complete is also called when establishing the TLS handshake, but should be ignored here.
            log.trace { "Received read complete while no data was read yet" }
        }
        super.channelReadComplete(ctx)
    }

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        super.handlerAdded(ctx)
        ctx.read()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        response.offer(Result.failure(cause))
    }

    private companion object {

        @JvmStatic
        val log = logger()
    }
}
