package io.qalipsis.plugins.netty

import io.micronaut.core.io.socket.SocketUtils
import io.netty.handler.codec.http2.Http2SecurityUtil
import io.netty.handler.ssl.ApplicationProtocolConfig
import io.netty.handler.ssl.ApplicationProtocolNames
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslProvider
import io.netty.handler.ssl.SupportedCipherSuiteFilter
import io.netty.handler.ssl.util.SelfSignedCertificate
import java.net.DatagramSocket
import java.net.InetSocketAddress

object ServerUtils {

    /**
     * Find an available TCP port on the local host.
     */
    fun availableTcpPort(): Int {
        return SocketUtils.findAvailableTcpPort()
    }

    /**
     * Find an available UDP port on the local host.
     */
    fun availableUdpPort(): Int {
        return DatagramSocket(0).use { it.localPort }
    }

    internal fun buildSslContext(tlsProtocols: Array<String> = emptyArray()): SslContext? {
        val certificate = SelfSignedCertificate()
        val builder = SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey())
        if (tlsProtocols.isNotEmpty()) {
            builder.protocols(*tlsProtocols)
        }
        return builder.build()
    }

    internal fun buildHttpSslContext(tlsProtocols: Array<String> = emptyArray()): SslContext? {
        val provider = if (SslProvider.isAlpnSupported(SslProvider.OPENSSL)) SslProvider.OPENSSL else SslProvider.JDK
        val ssc = SelfSignedCertificate()
        val builder = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).sslProvider(provider)
            .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
            .applicationProtocolConfig(
                ApplicationProtocolConfig(
                    ApplicationProtocolConfig.Protocol.ALPN,
                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                    ApplicationProtocolNames.HTTP_2,
                    ApplicationProtocolNames.HTTP_1_1
                )
            )
        if (tlsProtocols.isNotEmpty()) {
            builder.protocols(*tlsProtocols)
        }
        return builder.build()
    }

    internal fun buildListenAddress(host: String?, port: Int?): InetSocketAddress {
        return if (host.isNullOrBlank()) {
            if (port != null) {
                InetSocketAddress(port)
            } else {
                InetSocketAddress(availableTcpPort())
            }
        } else {
            if (port != null) {
                InetSocketAddress(host, port)
            } else {
                InetSocketAddress(host, availableTcpPort())
            }
        }
    }
}
