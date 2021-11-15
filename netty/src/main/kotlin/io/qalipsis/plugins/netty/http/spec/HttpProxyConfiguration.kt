package io.qalipsis.plugins.netty.http.spec

import io.qalipsis.api.annotations.Spec
import java.net.InetAddress
import javax.validation.constraints.NotNull
import javax.validation.constraints.Positive

/**
 * Configuration of a proxy for TCP connections.
 *
 * @property type protocol of the proxy to use, default to [TcpProxyType.SOCKS4]
 * @property host IP address or hostname of the proxy server
 * @property port port of the proxy server
 * @property username when the protocol supports it, username to connect to the proxy server
 * @property password when the protocol supports it, password to connect to the proxy server
 *
 * @author Eric Jessé
 */
@Spec
data class HttpProxyConfiguration internal constructor(
    internal var type: HttpProxyType = HttpProxyType.HTTP,
    @field:NotNull internal var host: String = "localhost",
    @field:NotNull @field:Positive internal var port: Int = 0,
    internal var username: String? = null,
    internal var password: String? = null
) {

    /**
     * Configures the host and port of the proxy server.
     */
    fun address(host: String, port: Int) {
        this.host = host
        this.port = port
    }

    /**
     * Configures the address and port of the proxy server.
     */
    fun address(address: InetAddress, port: Int) {
        this.host = address.hostAddress
        this.port = port
    }

    /**
     * Configures the authentication to identify to the proxy server.
     */
    fun authenticate(username: String?, password: String?) {
        this.username = username
        this.password = password
    }
}
