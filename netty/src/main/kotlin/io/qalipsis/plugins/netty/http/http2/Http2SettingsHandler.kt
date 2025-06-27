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
