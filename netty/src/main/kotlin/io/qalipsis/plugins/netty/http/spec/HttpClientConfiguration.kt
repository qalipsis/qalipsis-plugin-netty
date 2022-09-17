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

package io.qalipsis.plugins.netty.http.spec

import io.qalipsis.api.annotations.Spec
import io.qalipsis.plugins.netty.configuration.TlsConfiguration
import io.qalipsis.plugins.netty.socket.SocketClientConfiguration
import java.net.InetAddress
import java.net.URI
import java.nio.charset.Charset
import java.time.Duration
import javax.validation.Valid
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank

/**
 * Configuration of a raw TCP socket to connect to a peer.
 *
 * @author Eric Jessé
 */
@Spec
data class HttpClientConfiguration internal constructor(
    var version: HttpVersion = HttpVersion.HTTP_1_1,
    @field:NotBlank internal var scheme: String = "",
    @field:NotBlank internal var contextPath: String = "",
    override var connectTimeout: Duration = Duration.ofSeconds(10),
    override var noDelay: Boolean = true,
    override var keepConnectionAlive: Boolean = true,
    @field:Valid override var tlsConfiguration: TlsConfiguration? = null,
    @field:Valid internal var proxyConfiguration: HttpProxyConfiguration? = null,
    internal var charset: Charset = Charsets.UTF_8,
    @field:Min(1048576) internal var maxContentLength: Int = 1048576,
    internal var inflate: Boolean = false,
    internal var followRedirections: Boolean = false,
    @field:Min(1) internal var maxRedirections: Int = 10
) : SocketClientConfiguration(connectTimeout, noDelay, keepConnectionAlive, tlsConfiguration) {

    internal val isSecure: Boolean
        get() = scheme == "https"

    init {
        url("http://localhost")
    }

    /**
     * Enables and configures the connection to the remote peer via a proxy.
     */
    fun proxy(configurationBlock: HttpProxyConfiguration.() -> Unit) {
        this.proxyConfiguration = HttpProxyConfiguration().also { it.configurationBlock() }
    }

    /**
     * Configures the root url to access to the peer HTTP server, defaults to http://localhost.
     */
    fun url(url: String) {
        val uri = URI(url)
        scheme = uri.scheme
        host = uri.host
        inetAddress = InetAddress.getByName(host)
        port = uri.port.takeIf { it > 0 } ?: (if (isSecure) 443 else 80)
        contextPath = if (uri.rawPath.endsWith("/")) {
            uri.rawPath.substringBeforeLast("/")
        } else {
            uri.rawPath
        }
    }

    /**
     * Enables the inflation of requests and responses (if supported by the server).
     */
    fun inflate() {
        inflate = true
    }

    /**
     * Configures the charset of the requests and accepted for the responses, defaults to UTF-8.
     */
    fun charset(charset: Charset) {
        this.charset = charset
    }

    /**
     * Configures the maximal length of a single response in bytes, defaults to 1048576 bytes.
     */
    fun maxContentLength(maxContentLength: Int) {
        this.maxContentLength = maxContentLength
    }

    /**
     * Enables the following of redirections received with status 3xx, defaults to disabled.
     */
    fun followRedirections(max: Int = maxRedirections) {
        this.followRedirections = true
        this.maxRedirections = max
    }
}
