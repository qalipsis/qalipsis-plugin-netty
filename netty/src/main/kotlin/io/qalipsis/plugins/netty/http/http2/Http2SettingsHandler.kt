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

package io.qalipsis.plugins.netty.http.http2

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http2.Http2Settings
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.SuspendedCountLatch

/**
 * Reads the first [Http2Settings] object and notifies a [ChannelPromise].
 *
 * Reused and modified from Netty examples under license Apache 2.
 */
internal class Http2SettingsHandler(private val readyLatch: SuspendedCountLatch) :
    SimpleChannelInboundHandler<Http2Settings>() {

    init {
        readyLatch.blockingIncrement()
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: Http2Settings) {
        // Only care about the first settings message
        ctx.pipeline().remove(this)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        super.handlerRemoved(ctx)
        readyLatch.blockingDecrement()
    }

    private companion object {

        @JvmStatic
        val log = logger()
    }
}
