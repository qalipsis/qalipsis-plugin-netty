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
