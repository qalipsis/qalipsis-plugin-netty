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

package io.qalipsis.plugins.netty.http.client

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.proxy.HttpProxyHandler
import io.netty.handler.proxy.Socks5ProxyHandler
import io.qalipsis.plugins.netty.PipelineHandlerNames
import io.qalipsis.plugins.netty.http.spec.HttpProxyConfiguration
import io.qalipsis.plugins.netty.http.spec.HttpProxyType
import io.qalipsis.plugins.netty.http.spec.HttpVersion
import java.net.InetSocketAddress

/**
 * Initializer of a [SocketChannel] for the HTTP clients.
 *
 * @author Eric Jessé
 */
internal abstract class HttpChannelInitializer : ChannelInitializer<SocketChannel>() {

    abstract val requestExecutionConfigurer: HttpRequestExecutionConfigurer

    abstract val version: HttpVersion

    protected fun configureProxyHandler(channel: SocketChannel, configuration: HttpProxyConfiguration) {
        val proxyHandler = when (configuration.type) {
            HttpProxyType.SOCKS5 -> Socks5ProxyHandler(
                InetSocketAddress(configuration.host, configuration.port),
                configuration.username,
                configuration.password
            )
            HttpProxyType.HTTP -> configuration.username?.takeIf(String::isNotEmpty)?.let { username ->
                HttpProxyHandler(
                    InetSocketAddress(configuration.host, configuration.port),
                    username,
                    configuration.password
                )
            } ?: HttpProxyHandler(InetSocketAddress(configuration.host, configuration.port))
        }
        channel.pipeline().addFirst(PipelineHandlerNames.PROXY_HANDLER, proxyHandler)
    }
}
