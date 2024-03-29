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

package io.qalipsis.plugins.netty.proxy.server.handlers

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.util.ReferenceCountUtil
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.netty.proxy.server.OutboundChannelClosedEvent
import io.qalipsis.plugins.netty.proxy.server.ProxyingContext
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * Handler for the back-end of a HTTP 1 proxying context.
 *
 * When a full request is received, it is kept in a queue to match with the response and forwarded to the back-end.
 *
 */
internal class Http1BackendHandler(
    private val proxyingContext: ProxyingContext,
    private val requestCounter: AtomicInteger
) : SimpleChannelInboundHandler<HttpObject>() {

    private val requestForwarder = QueuedOutboundHandler()

    @Volatile
    private var currentRequest: HttpRequest? = null

    override fun channelActive(ctx: ChannelHandlerContext) {
        requestForwarder.writeNextIfAny()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        requestForwarder.release()
        proxyingContext.frontEndChannel.pipeline()
            .fireUserEventTriggered(OutboundChannelClosedEvent(proxyingContext, false))
    }

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        ctx.pipeline()
            .addBefore(ctx.name(), null, HttpClientCodec())
            .addBefore(ctx.name(), null, requestForwarder)
    }

    override fun channelRead0(ctx: ChannelHandlerContext, httpObject: HttpObject) {
        log.trace { "Channel ${ctx.channel()}: forwarding response to the front-end $httpObject" }
        proxyingContext.frontEndChannel.writeAndFlush(ReferenceCountUtil.retain(httpObject))
        if (httpObject is HttpResponse) {
            log.trace { "Channel ${ctx.channel()}: writing the pending response to the front-end" }
            currentRequest = null
            requestForwarder.writeNextIfAny()
        }
    }

    private inner class QueuedOutboundHandler : ChannelOutboundHandlerAdapter() {

        private val pendingRequests = ConcurrentLinkedDeque<RequestPromise>()

        private lateinit var handlerContext: ChannelHandlerContext

        override fun handlerAdded(ctx: ChannelHandlerContext) {
            handlerContext = ctx.pipeline().context(this)
        }

        override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
            if (msg is FullHttpRequest) {
                log.trace { "Channel ${ctx.channel()}: adding the request into the queue to the backend" }
                requestCounter.incrementAndGet()
                val request = msg as HttpRequest
                pendingRequests.offer(RequestPromise(request, promise))
                writeNextIfAny()
            } else {
                check(msg !is HttpObject) { "Cannot handled message: ${msg.javaClass}" }
                ctx.write(msg, promise)
            }
        }

        fun writeNextIfAny() {
            if (currentRequest != null || !handlerContext.channel().isActive || pendingRequests.isEmpty()) {
                log.trace { "Channel ${handlerContext.channel()}: request cannot be sent to the backend, currentRequest is $currentRequest, channel is active? ${handlerContext.channel().isActive}, ${pendingRequests.size} pending request(s)" }
                return
            }
            val requestPromise = pendingRequests.poll()
            currentRequest = requestPromise.request
            log.trace { "Channel ${handlerContext.channel()}: sending request to the backend ${requestPromise.request}" }
            handlerContext.writeAndFlush(requestPromise.request, requestPromise.promise)
        }

        fun release() {
            while (pendingRequests.isNotEmpty()) {
                val requestPromise: RequestPromise = pendingRequests.poll()
                requestPromise.promise.setFailure(IOException("Cannot send request to server"))
                ReferenceCountUtil.release(requestPromise.request)
            }
        }
    }

    private data class RequestPromise(val request: HttpRequest, val promise: ChannelPromise)

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
