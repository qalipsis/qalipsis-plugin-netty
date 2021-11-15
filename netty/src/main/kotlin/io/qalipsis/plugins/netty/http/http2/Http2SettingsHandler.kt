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
