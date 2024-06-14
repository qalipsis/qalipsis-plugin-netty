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

package io.qalipsis.plugins.netty.http.http2

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelPipeline
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpClientUpgradeHandler
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.DefaultHttp2Connection
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec
import io.netty.handler.codec.http2.Http2Connection
import io.netty.handler.codec.http2.Http2FrameLogger
import io.netty.handler.codec.http2.Http2SecurityUtil
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder
import io.netty.handler.logging.LogLevel
import io.netty.handler.ssl.ApplicationProtocolConfig
import io.netty.handler.ssl.ApplicationProtocolNames
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslProvider
import io.netty.handler.ssl.SupportedCipherSuiteFilter
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.plugins.netty.PipelineHandlerNames
import io.qalipsis.plugins.netty.configuration.TlsConfiguration
import io.qalipsis.plugins.netty.handlers.monitoring.ConnectionMonitoringHandler
import io.qalipsis.plugins.netty.handlers.monitoring.TlsMonitoringHandler
import io.qalipsis.plugins.netty.http.HttpPipelineNames
import io.qalipsis.plugins.netty.http.client.HttpChannelInitializer
import io.qalipsis.plugins.netty.http.client.HttpRequestExecutionConfigurer
import io.qalipsis.plugins.netty.http.http1.Http1ChannelInitializer
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.monitoring.MonitoringCollector
import java.net.InetSocketAddress

/**
 * Configures the client pipeline to support HTTP/2 frames. If the server does not support HTTP/2, the channel
 * is configured for HTTP/1.1.
 *
 * @author Eric Jessé
 */
internal class Http2ChannelInitializer(
    private val clientConfiguration: HttpClientConfiguration,
    private val monitoringCollector: MonitoringCollector,
    private val readyLatch: SuspendedCountLatch
) : HttpChannelInitializer() {

    override lateinit var requestExecutionConfigurer: HttpRequestExecutionConfigurer

    override var version = io.qalipsis.plugins.netty.http.spec.HttpVersion.HTTP_2_0

    private var connectionHandler: HttpToHttp2ConnectionHandler? = null

    public override fun initChannel(ch: SocketChannel) {
        log.debug { "Starting the initialization of the HTTP2 channel ${ch.pipeline()}" }
        clientConfiguration.proxyConfiguration?.apply {
            log.debug { "Configuring the proxy for the HTTP2 channel ${ch.pipeline()}" }
            configureProxyHandler(ch, this)
        }

        val connection: Http2Connection = DefaultHttp2Connection(false)
        connectionHandler = HttpToHttp2ConnectionHandlerBuilder()
            .validateHeaders(true)
            .frameListener(
                DelegatingDecompressorFrameListener(
                    connection,
                    InboundHttp2ToHttpAdapterBuilder(connection)
                        .maxContentLength(MAX_CONTENT_LENGTH)
                        .validateHttpHeaders(true)
                        .propagateSettings(true)
                        .build()
                )
            )
            .connection(connection)
            .also {
                if (log.isDebugEnabled) {
                    it.frameLogger(debuggingLogger)
                }
            }
            .build()

        ch.pipeline().addLast(ConnectionMonitoringHandler(monitoringCollector, readyLatch))
        readyLatch.blockingIncrement()
        if (clientConfiguration.isSecure) {
            configureSsl(ch, clientConfiguration.tlsConfiguration ?: TlsConfiguration())
        } else {
            configureClearText(ch)
        }
    }

    private fun configureSsl(channel: SocketChannel, tlsConfiguration: TlsConfiguration) {
        log.debug { "Configuring the end of HTTP2 - with SSL - pipeline ${channel.pipeline()}" }
        val provider = if (SslProvider.isAlpnSupported(SslProvider.OPENSSL)) SslProvider.OPENSSL else SslProvider.JDK
        val sslContext = if (tlsConfiguration.disableCertificateVerification) {
            SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
        } else {
            SslContextBuilder.forClient()
        }.sslProvider(provider)
            .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
            .applicationProtocolConfig(
                ApplicationProtocolConfig(
                    ApplicationProtocolConfig.Protocol.ALPN,
                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE, // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT, // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                    ApplicationProtocolNames.HTTP_2,
                    ApplicationProtocolNames.HTTP_1_1
                )
            ).build()

        channel.pipeline()
            .addLast(sslContext.newHandler(channel.alloc(), clientConfiguration.host, clientConfiguration.port))
        channel.pipeline().addLast(TlsMonitoringHandler(monitoringCollector, readyLatch))
        // We must wait for the handshake to finish and the protocol to be negotiated before configuring
        // the HTTP/2 components of the pipeline.
        channel.pipeline().addLast(object : ApplicationProtocolNegotiationHandler("") {
            override fun configurePipeline(ctx: ChannelHandlerContext, protocol: String) {
                val pipeline = ctx.pipeline()
                log.debug { "Configuring pipeline $pipeline for protocol $protocol" }
                if (ApplicationProtocolNames.HTTP_2 == protocol) {
                    channel.pipeline().addLast(PipelineHandlerNames.CONNECTION_HANDLER, connectionHandler)
                    configureEndOfPipeline(pipeline)
                } else if (ApplicationProtocolNames.HTTP_1_1 == protocol) {
                    // The pipeline is configured to be used as an HTTP 1 client.
                    readyLatch.blockingIncrement()
                    val http1Initializer =
                        Http1ChannelInitializer(clientConfiguration, monitoringCollector, readyLatch)
                    http1Initializer.configureEndOfPipeline(pipeline)
                    version = http1Initializer.version
                    requestExecutionConfigurer = http1Initializer.requestExecutionConfigurer
                    readyLatch.blockingDecrement()
                } else {
                    ctx.close();
                    throw IllegalStateException("Unknown Protocol: $protocol");
                }
            }

            override fun handshakeFailure(ctx: ChannelHandlerContext?, cause: Throwable?) {
                super.handshakeFailure(ctx, cause)
                readyLatch.blockingDecrement()
            }
        })
    }

    private fun configureEndOfPipeline(pipeline: ChannelPipeline) {
        log.debug { "Configuring the end of HTTP2 pipeline $pipeline" }
        pipeline.addLast(HttpPipelineNames.HTTP2_SETTINGS_HANDLER, Http2SettingsHandler(readyLatch))
        requestExecutionConfigurer = Http2RequestExecutionConfigurer(clientConfiguration, pipeline)
        readyLatch.blockingDecrement()
    }

    /**
     * Configures the pipeline for a cleartext upgrade from HTTP to HTTP/2.
     */
    private fun configureClearText(ch: SocketChannel) {
        log.debug { "Configuring the end of HTTP2 cleartext pipeline ${ch.pipeline()}" }
        // Adds a no-op connection handler to be able to add the further handlers after it.
        ch.pipeline().addLast(PipelineHandlerNames.CONNECTION_HANDLER, ChannelDuplexHandler())

        val sourceCodec = HttpClientCodec()
        val upgradeCodec = Http2ClientUpgradeCodec(HttpPipelineNames.HTTP2_UPGRADE_CODEC, connectionHandler)
        val upgradeHandler = HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 65536)
        ch.pipeline().addLast(sourceCodec, upgradeHandler, UpgradeRequestHandler())
    }

    /**
     * A handler that triggers the cleartext upgrade to HTTP/2 by sending an initial HTTP request.
     */
    private inner class UpgradeRequestHandler : ChannelInboundHandlerAdapter() {
        override fun channelActive(ctx: ChannelHandlerContext) {
            val upgradeRequest =
                DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER)

            // Set HOST header as the remote peer may require it.
            val remote = ctx.channel().remoteAddress() as InetSocketAddress
            var hostString = remote.hostString
            if (hostString == null) {
                hostString = remote.address.hostAddress
            }
            upgradeRequest.headers()[HttpHeaderNames.HOST] = hostString + ':' + remote.port
            ctx.writeAndFlush(upgradeRequest)
            ctx.fireChannelActive()

            // Done with this handler, remove it from the pipeline.
            ctx.pipeline().remove(this)
            log.debug { "UpgradeRequestHandler active for the HTTP2 cleartext pipeline ${ctx.pipeline()}" }
            configureEndOfPipeline(ctx.pipeline())
        }
    }

    companion object {

        private const val MAX_CONTENT_LENGTH = Integer.MAX_VALUE

        @JvmStatic
        private val debuggingLogger = Http2FrameLogger(LogLevel.INFO, Http2ChannelInitializer::class.java)

        @JvmStatic
        private val log = logger()

    }
}
