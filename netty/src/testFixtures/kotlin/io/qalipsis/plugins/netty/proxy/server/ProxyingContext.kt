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

package io.qalipsis.plugins.netty.proxy.server

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.netty.proxy.server.handlers.Http1BackendHandler
import io.qalipsis.plugins.netty.proxy.server.handlers.Http1FrontendHandler
import io.qalipsis.plugins.netty.proxy.server.handlers.SslProxyHandler
import java.util.concurrent.atomic.AtomicInteger

internal class ProxyingContext(
    private val proxyConfiguration: ProxyConfiguration,
    private val frontEndAddress: Address,
    val frontEndChannel: Channel,
    private val requestCounter: AtomicInteger
) {

    var backEndAddress: Address? = null

    var backendChannel: Channel? = null

    val isConnected: Boolean
        get() = backendChannel != null

    val isSecured: Boolean
        get() = backEndAddress?.port in proxyConfiguration.securedPorts

    /**
     * Creates a [ChannelHandler] for the kind of proxying step to perform.
     */
    fun handler(handler: Handler): ChannelHandler {
        return when (handler) {
            Handler.HTTP1_FRONTEND -> Http1FrontendHandler(this)
            Handler.HTTP1_BACKEND -> Http1BackendHandler(this, requestCounter)
            Handler.SSL_FRONTEND -> SslProxyHandler(this, true)
            Handler.SSL_BACKEND -> SslProxyHandler(this, false)
            Handler.HTTP2_FRONTEND -> Http1FrontendHandler(this)
            Handler.HTTP2_BACKEND -> Http1BackendHandler(this, requestCounter)
        }
    }

    fun connectToBackend(backendAddress: Address, fromCtx: ChannelHandlerContext): ChannelFuture {
        if (backendChannel != null && (this.backEndAddress != backendAddress || !backendChannel!!.isActive)) {
            backendChannel?.close()
            backendChannel = null
        }

        return if (backendChannel != null) {
            backendChannel!!.newSucceededFuture()
        } else {
            this.backEndAddress = backendAddress
            Bootstrap()
                .group(fromCtx.channel().eventLoop())
                .channel(fromCtx.channel().javaClass)
                .handler(object : ChannelInitializer<Channel>() {

                    override fun initChannel(ch: Channel) {
                        this@ProxyingContext.backendChannel = ch
                        ch.pipeline().addLast(handler(Handler.SSL_BACKEND))
                    }

                }).connect(backendAddress.host, backendAddress.port).also {
                    it.addListener { f ->
                        if (!f.isSuccess) {
                            log.error(f.cause().message, f.cause())
                        }
                    }
                }
        }
    }

    data class Address(val host: String, val port: Int)

    companion object {

        @JvmStatic
        private val log = logger()
    }

}
