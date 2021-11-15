package io.qalipsis.plugins.netty.proxy.server.handlers

import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.handler.ssl.ApplicationProtocolNames
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler
import io.netty.handler.ssl.SslContext
import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.netty.proxy.server.Handler
import io.qalipsis.plugins.netty.proxy.server.ProxyingContext
import io.qalipsis.plugins.netty.proxy.server.SslUtils

internal class SslProxyHandler(
    private val proxyingContext: ProxyingContext,
    private val isFrontEnd: Boolean
) : ChannelOutboundHandlerAdapter() {

    private val outboundChannel: Channel =
        if (isFrontEnd) proxyingContext.backendChannel!! else proxyingContext.frontEndChannel

    private val pendingRequests = concurrentList<Any>()

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        if (proxyingContext.isSecured) {
            log.trace { "Channel ${ctx.channel()}: creating handler with SSL context" }
            val sslHandler = sslCtx().newHandler(ctx.alloc())
            ctx.pipeline()
                .addBefore(ctx.name(), null, sslHandler)
                .addBefore(ctx.name(), null, HttpProtocolHandler(ctx))
        } else {
            log.trace { "Channel ${ctx.channel()}: creating handler without SSL context" }
            configHttp1(ctx)
        }
    }

    private fun sslCtx(): SslContext {
        return if (isFrontEnd) {
            SslUtils.buildContextForFrontEnd()
        } else {
            SslUtils.buildContextForBackend()
        }
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        flushPendingRequests(ctx)
        ctx.flush()
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        log.trace { "Channel ${ctx.channel()}: adding pending message $msg" }
        pendingRequests.add(msg)
        if (ctx.isRemoved) {
            flushPendingRequests(ctx)
            ctx.flush()
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        log.trace { "Channel ${ctx.channel()}: exception caught ${cause}" }
        outboundChannel.close()
        ctx.close()
    }

    private fun flushPendingRequests(ctx: ChannelHandlerContext) {
        val iterator = pendingRequests.iterator()
        while (iterator.hasNext()) {
            ctx.write(iterator.next())
            try {
                iterator.remove()
            } catch (e: Exception) {
                logger().error(e.message, e)
            }
        }
    }

    private fun configHttp1(ctx: ChannelHandlerContext) {
        if (isFrontEnd) {
            log.trace { "Channel ${ctx.channel()}: creating handler for the frontend" }
            ctx.pipeline().replace(this, null, proxyingContext.handler(Handler.HTTP1_FRONTEND))
        } else {
            log.trace { "Channel ${ctx.channel()}: creating handler for the backend" }
            ctx.pipeline().replace(this, null, proxyingContext.handler(Handler.HTTP1_BACKEND))
        }
    }

    private fun configHttp2(ctx: ChannelHandlerContext) {
        if (isFrontEnd) {
            log.trace { "Channel ${ctx.channel()}: creating handler for the frontend" }
            ctx.pipeline().replace(this, null, proxyingContext.handler(Handler.HTTP2_FRONTEND))
        } else {
            log.trace { "Channel ${ctx.channel()}: creating handler for the backend" }
            ctx.pipeline().replace(this, null, proxyingContext.handler(Handler.HTTP2_BACKEND))
        }
    }

    private inner class HttpProtocolHandler(private val sslCtx: ChannelHandlerContext) :
        ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {

        override fun configurePipeline(ctx: ChannelHandlerContext, protocol: String) = when {
            ApplicationProtocolNames.HTTP_1_1 == protocol -> configHttp1(sslCtx)
            ApplicationProtocolNames.HTTP_2 == protocol -> configHttp2(sslCtx)
            else -> throw IllegalStateException("Unsupported HTTP protocol: $protocol")
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()

    }

}
