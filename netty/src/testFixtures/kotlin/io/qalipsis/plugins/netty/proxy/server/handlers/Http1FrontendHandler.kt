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

import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.*
import io.netty.util.ReferenceCountUtil
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.netty.proxy.server.Handler
import io.qalipsis.plugins.netty.proxy.server.OutboundChannelClosedEvent
import io.qalipsis.plugins.netty.proxy.server.ProxyingContext
import java.util.regex.Pattern

internal class Http1FrontendHandler(private val proxyingContext: ProxyingContext) :
    SimpleChannelInboundHandler<FullHttpRequest>() {

    private val httpServerCodec = HttpServerCodec()

    private val httpObjectAggregator = HttpObjectAggregator(1024 * 1024 * 40) // 40MB

    private val tunneled = proxyingContext.isConnected

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        ctx.pipeline()
            .addBefore(ctx.name(), null, httpServerCodec)
            .addBefore(ctx.name(), null, httpObjectAggregator)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        ctx.pipeline().remove(httpServerCodec).remove(httpObjectAggregator)

        if (tunneled) {
            proxyingContext.backendChannel?.close()
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any?) {
        if (evt is OutboundChannelClosedEvent && tunneled) {
            ctx.close()
        }
        ctx.fireUserEventTriggered(evt)
    }

    override fun channelRead0(ctx: ChannelHandlerContext, request: FullHttpRequest) {
        if (!tunneled) {
            if (request.method() === HttpMethod.CONNECT) {
                handleProxyConnection(ctx, request)
            } else {
                forwardRequest(ctx, request)
            }
        } else {
            proxyingContext.backendChannel?.writeAndFlush(ReferenceCountUtil.retain(request))
        }
    }

    private fun handleProxyConnection(ctx: ChannelHandlerContext, request: FullHttpRequest) {
        val address: ProxyingContext.Address = resolveBackendAddress(request.uri())
        createOutboundChannel(ctx, address).addListener { future ->
            if (future.isSuccess) {
                val response = DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.OK)
                ctx.writeAndFlush(response)
                ctx.pipeline().replace(
                    this@Http1FrontendHandler, "frontendSsl",
                    proxyingContext.handler(Handler.SSL_FRONTEND)
                )
            }
        }
    }

    private fun forwardRequest(ctx: ChannelHandlerContext, request: FullHttpRequest) {
        val fullPath = resolveHttpProxyPath(request.uri())
        val channelFuture = proxyingContext.connectToBackend(ProxyingContext.Address(fullPath.host, fullPath.port), ctx)
        log.trace { "Channel ${ctx.channel()}: connecting to backend" }
        channelFuture.addListener { future ->
            if (future.isSuccess) {
                log.trace { "Channel ${ctx.channel()}: connected to backend, forwarding to $fullPath request $request" }
                channelFuture.channel().writeAndFlush(request.copy()).addListener {
                    if (it.isSuccess) {
                        log.trace { "Channel ${ctx.channel()}: request successfully written to the backend" }
                    } else {
                        log.trace { "Channel ${ctx.channel()}: request failed to be sent to the backend, ${it.cause()}" }
                    }
                }
            } else {
                log.trace { "Channel ${ctx.channel()}: connection failed, ${future.cause()}" }
                ctx.channel().close()
            }
        }
    }

    private fun resolveHttpProxyPath(fullPath: String): FullPath {
        val matcher = PATH_PATTERN.matcher(fullPath)
        return if (matcher.find()) {
            val scheme = matcher.group(1)
            val host = matcher.group(2)
            val port = resolvePort(scheme, matcher.group(4))
            val path = matcher.group(5)
            FullPath(scheme, host, port, path)
        } else {
            throw IllegalStateException("Illegal http proxy path: $fullPath")
        }
    }

    private fun resolveBackendAddress(address: String): ProxyingContext.Address {
        val matcher = BACKEND_ADDRESS_PATTERN.matcher(address)
        return if (matcher.find()) {
            ProxyingContext.Address(matcher.group(1), matcher.group(2).toInt())
        } else {
            throw IllegalStateException("Illegal backend address: $address")
        }
    }

    private fun resolvePort(scheme: String?, port: String?): Int {
        return if (port.isNullOrBlank()) {
            if ("https" == scheme?.lowercase()) 443 else 80
        } else port.toInt()
    }

    private fun createOutboundChannel(
        ctx: ChannelHandlerContext,
        backendAddress: ProxyingContext.Address
    ): ChannelFuture {
        val future: ChannelFuture = proxyingContext.connectToBackend(backendAddress, ctx)
        future.addListener { f ->
            if (!f.isSuccess) {
                ctx.channel().close()
            }
        }
        return future
    }

    private data class FullPath(val scheme: String, val host: String, val port: Int, val path: String)

    companion object {

        @JvmStatic
        private val PATH_PATTERN = Pattern.compile("(https?)://([a-zA-Z0-9\\.\\-]+)(:(\\d+))?(/.*)")

        @JvmStatic
        private val BACKEND_ADDRESS_PATTERN = Pattern.compile("^([a-zA-Z0-9\\.\\-_]+):(\\d+)")

        @JvmStatic
        private val log = logger()
    }

}
