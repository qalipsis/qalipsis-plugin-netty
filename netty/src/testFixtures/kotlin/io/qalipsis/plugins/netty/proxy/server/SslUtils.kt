package io.qalipsis.plugins.netty.proxy.server

import io.netty.handler.codec.http2.Http2SecurityUtil
import io.netty.handler.ssl.ApplicationProtocolConfig
import io.netty.handler.ssl.ApplicationProtocolNames
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SupportedCipherSuiteFilter
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.ssl.util.SelfSignedCertificate

internal object SslUtils {

    fun buildContextForBackend(): SslContext {
        val builder: SslContextBuilder = SslContextBuilder.forClient()
            .configure().trustManager(InsecureTrustManagerFactory.INSTANCE)
        return builder.build()
    }

    fun buildContextForFrontEnd(): SslContext {
        val certificate = SelfSignedCertificate()
        return SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey())
            .configure()
            .build()
    }

    private fun SslContextBuilder.configure(): SslContextBuilder {
        return this.ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
            .applicationProtocolConfig(
                ApplicationProtocolConfig(
                    ApplicationProtocolConfig.Protocol.ALPN,
                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                    ApplicationProtocolNames.HTTP_2,
                    ApplicationProtocolNames.HTTP_1_1
                )
            )
    }

}
