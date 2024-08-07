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

package io.qalipsis.plugins.netty.http.http1

import io.netty.channel.ChannelPipeline
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpContentDecompressor
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.SslProvider
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.plugins.netty.PipelineHandlerNames
import io.qalipsis.plugins.netty.configuration.TlsConfiguration
import io.qalipsis.plugins.netty.handlers.monitoring.ConnectionMonitoringHandler
import io.qalipsis.plugins.netty.handlers.monitoring.TlsMonitoringHandler
import io.qalipsis.plugins.netty.http.HttpPipelineNames.AGGREGATOR_HANDLER
import io.qalipsis.plugins.netty.http.client.HttpChannelInitializer
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.http.spec.HttpVersion
import io.qalipsis.plugins.netty.monitoring.MonitoringCollector

/**
 * Channel initializer for HTTP/1.1 clients.
 *
 * @author Eric Jessé
 */
internal class Http1ChannelInitializer(
    private val clientConfiguration: HttpClientConfiguration,
    private val monitoringCollector: MonitoringCollector,
    private val readyLatch: SuspendedCountLatch
) : HttpChannelInitializer() {

    override lateinit var requestExecutionConfigurer: Http1RequestExecutionConfigurer

    override val version: HttpVersion = HttpVersion.HTTP_1_1

    override fun initChannel(channel: SocketChannel) {
        val pipeline = channel.pipeline()

        clientConfiguration.proxyConfiguration?.apply {
            configureProxyHandler(channel, this)
        }
        pipeline.addLast(ConnectionMonitoringHandler(monitoringCollector, readyLatch))
        if (clientConfiguration.isSecure) {
            configureSsl(channel, clientConfiguration.tlsConfiguration ?: TlsConfiguration())
        }
        configureEndOfPipeline(pipeline)
    }

    fun configureEndOfPipeline(pipeline: ChannelPipeline) {
        pipeline.addLast(PipelineHandlerNames.CLIENT_CODEC, HttpClientCodec())
        pipeline.addLast(PipelineHandlerNames.RESPONSE_DECOMPRESSOR, HttpContentDecompressor())

        pipeline.addLast(AGGREGATOR_HANDLER, Http1ComposableObjectAggregator(clientConfiguration.maxContentLength))
        requestExecutionConfigurer = Http1RequestExecutionConfigurer(clientConfiguration, pipeline)
    }

    private fun configureSsl(channel: SocketChannel, tlsConfiguration: TlsConfiguration) {
        val provider = if (SslProvider.isAlpnSupported(SslProvider.OPENSSL)) SslProvider.OPENSSL else SslProvider.JDK
        val sslContext = if (tlsConfiguration.disableCertificateVerification) {
            SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
        } else {
            SslContextBuilder.forClient()
        }.sslProvider(provider).build()
        val sslEngine = sslContext.newEngine(channel.alloc(), clientConfiguration.host, clientConfiguration.port)
        sslEngine.enabledProtocols =
            tlsConfiguration.protocols.takeIf(Array<String>::isNotEmpty) ?: sslEngine.supportedProtocols
        channel.pipeline().addLast(SslHandler(sslEngine).apply {
            handshakeTimeoutMillis = clientConfiguration.connectTimeout.toMillis()
        })
        channel.pipeline().addLast(TlsMonitoringHandler(monitoringCollector, readyLatch))
    }

}
