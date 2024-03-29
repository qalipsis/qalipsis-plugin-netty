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

package io.qalipsis.plugins.netty.proxy.server

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.EventLoopGroup
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.ServerUtils
import io.qalipsis.plugins.netty.proxy.server.handlers.HttpProxyInitializer
import io.qalipsis.plugins.netty.proxy.server.handlers.SocksServerInitializer
import io.qalipsis.plugins.netty.tcp.server.TcpServer
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.concurrent.atomic.AtomicInteger


/**
 * Instance of a local proxy server for test purpose.
 *
 * @author Eric Jessé
 */
class ProxyServer private constructor(
    port: Int,
    bootstrap: ServerBootstrap,
    eventGroups: Collection<EventLoopGroup>,
    private val requestCounter: AtomicInteger,
    private val securedPorts: MutableCollection<Int>
) : TcpServer(port, bootstrap, eventGroups), AfterEachCallback {

    /**
     * Returns the number of received complete requests so far.
     */
    val requestCount: Int
        get() = requestCounter.get()

    /**
     * Resets the counter of received complete requests.
     */
    fun resetRequestCount() {
        requestCounter.set(0)
    }

    fun addSecuredPorts(vararg ports: Int) {
        securedPorts.addAll(ports.toList())
    }

    override fun afterEach(context: ExtensionContext) {
        resetRequestCount()
    }

    companion object {

        /**
         * Builds a new HTTP proxy server. The server is not started yet once built. You can use it either as a JUnit extension or start and stop manually.
         *
         * @param host the host name to listen, or null (default) or all have to be read.
         * @param port the port to use, or null (default) to use a random available one.
         * @param securedPorts list of backend ports to use with SSL.
         */
        @JvmStatic
        fun newHttp(host: String? = null, port: Int? = null, securedPorts: Collection<Int> = setOf(443, 8443)) =
            new(ProxyType.HTTP, host, port, securedPorts)

        /**
         * Builds a new Socks proxy server. The server is not started yet once built. You can use it either as a JUnit extension or start and stop manually.
         *
         * @param host the host name to listen, or null (default) or all have to be read.
         * @param port the port to use, or null (default) to use a random available one.
         * @param securedPorts list of backend ports to use with SSL.
         */
        @JvmStatic
        fun newSocks(host: String? = null, port: Int? = null) = new(ProxyType.SOCKS, host, port, emptySet())

        /**
         * Builds a new proxy server. The server is not started yet once built. You can use it either as a JUnit extension or start and stop manually.
         *
         * @param type the type of the server.
         * @param host the host name to listen, or null (default) or all have to be read.
         * @param port the port to use, or null (default) to use a random available one.
         * @param securedPorts list of backend ports to use with SSL.
         */
        @JvmStatic
        private fun new(
            type: ProxyType,
            host: String? = null,
            port: Int? = null,
            securedPorts: Collection<Int>
        ): ProxyServer {
            val mutableSecuredPorts = mutableSetOf<Int>()
            mutableSecuredPorts += securedPorts
            val configuration = ProxyConfiguration(mutableSecuredPorts)
            val bossGroup = NativeTransportUtils.getEventLoopGroup()
            val workerGroup = NativeTransportUtils.getEventLoopGroup()

            val inetSocketAddress = ServerUtils.buildListenAddress(host, port)
            val requestCounter = AtomicInteger()
            val initializer =
                if (type == ProxyType.HTTP) HttpProxyInitializer(
                    configuration,
                    requestCounter
                ) else SocksServerInitializer(requestCounter)

            val bootstrap = ServerBootstrap().group(bossGroup, workerGroup)
                .channel(NativeTransportUtils.serverSocketChannelClass)
                .localAddress(inetSocketAddress)
                .childHandler(initializer)
            return ProxyServer(
                inetSocketAddress.port,
                bootstrap,
                listOf(bossGroup, workerGroup),
                requestCounter,
                mutableSecuredPorts
            )
        }
    }

}
