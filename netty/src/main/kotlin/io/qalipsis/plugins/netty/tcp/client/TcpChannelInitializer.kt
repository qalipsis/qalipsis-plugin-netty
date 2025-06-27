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

package io.qalipsis.plugins.netty.tcp.client

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.bytes.ByteArrayDecoder
import io.netty.handler.codec.bytes.ByteArrayEncoder
import io.netty.handler.proxy.Socks4ProxyHandler
import io.netty.handler.proxy.Socks5ProxyHandler
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.plugins.netty.PipelineHandlerNames
import io.qalipsis.plugins.netty.configuration.TlsConfiguration
import io.qalipsis.plugins.netty.handlers.monitoring.ConnectionMonitoringHandler
import io.qalipsis.plugins.netty.handlers.monitoring.TlsMonitoringHandler
import io.qalipsis.plugins.netty.monitoring.MonitoringCollector
import io.qalipsis.plugins.netty.tcp.spec.TcpClientConfiguration
import io.qalipsis.plugins.netty.tcp.spec.TcpProxyConfiguration
import io.qalipsis.plugins.netty.tcp.spec.TcpProxyType
import java.net.InetSocketAddress

/**
 * Channel initializer for TCP clients.
 *
 * @author Eric Jessé
 */
internal class TcpChannelInitializer(
    private val connectionConfiguration: TcpClientConfiguration,
    private val monitoringCollector: MonitoringCollector,
    private val readyLatch: SuspendedCountLatch
) : ChannelInitializer<SocketChannel>() {

    override fun initChannel(channel: SocketChannel) {
        val pipeline = channel.pipeline()

        connectionConfiguration.proxyConfiguration?.apply {
            configureProxyHandler(channel, this)
        }
        channel.pipeline().addLast(ConnectionMonitoringHandler(monitoringCollector, readyLatch))

        connectionConfiguration.tlsConfiguration?.apply {
            configureTlsHandler(channel, this)
        }
        channel.pipeline().addLast(PipelineHandlerNames.REQUEST_DECODER, ByteArrayDecoder())
        channel.pipeline().addLast(PipelineHandlerNames.REQUEST_ENCODER, ByteArrayEncoder())

        // The handler is no longer required once everything was initialized.
        pipeline.remove(this)
    }

    private fun configureProxyHandler(channel: SocketChannel, configuration: TcpProxyConfiguration) {
        val proxyHandler = when (configuration.type) {
            TcpProxyType.SOCKS4 -> Socks4ProxyHandler(
                InetSocketAddress(configuration.host, configuration.port),
                configuration.username
            )
            TcpProxyType.SOCKS5 -> Socks5ProxyHandler(
                InetSocketAddress(configuration.host, configuration.port),
                configuration.username,
                configuration.password
            )
        }
        channel.pipeline().addFirst(PipelineHandlerNames.PROXY_HANDLER, proxyHandler)
    }

    private fun configureTlsHandler(channel: SocketChannel, tlsConfiguration: TlsConfiguration) {
        val sslContextBuilder = if (tlsConfiguration.disableCertificateVerification) {
            SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
        } else {
            SslContextBuilder.forClient()
        }
        val sslEngine = sslContextBuilder.build().newEngine(channel.alloc())
        if (tlsConfiguration.protocols.isNotEmpty()) {
            sslEngine.enabledProtocols = tlsConfiguration.protocols
        } else {
            sslEngine.enabledProtocols = sslEngine.supportedProtocols
        }
        channel.pipeline().addLast(SslHandler(sslEngine))
        channel.pipeline().addLast(TlsMonitoringHandler(monitoringCollector, readyLatch))
    }

}
