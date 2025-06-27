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

package io.qalipsis.plugins.netty.tcp.spec

import io.qalipsis.api.annotations.Spec
import io.qalipsis.plugins.netty.configuration.TlsConfiguration
import io.qalipsis.plugins.netty.socket.SocketClientConfiguration
import java.time.Duration
import javax.validation.Valid

/**
 * Configuration of a raw TCP socket to connect to a peer.
 *
 * @property noDelay if set to `true` (default), the Nagle's algorithm is disabled
 * @property keepConnectionAlive keep the connection open even if there is no other step using it (this is required when the connection is used between different operations)
 * @property numberOfThreads number of threads of the event worker from Netty
 *
 * @author Eric Jessé
 */
@Spec
class TcpClientConfiguration internal constructor(
    connectTimeout: Duration = Duration.ofSeconds(10),
    noDelay: Boolean = true,
    keepConnectionAlive: Boolean = false,
    tlsConfiguration: TlsConfiguration? = null,
    @field:Valid internal var proxyConfiguration: TcpProxyConfiguration? = null
) : SocketClientConfiguration(connectTimeout, noDelay, keepConnectionAlive, tlsConfiguration) {

    /**
     * Enables and configures the connection to the remote peer via a proxy.
     */
    fun proxy(configurationBlock: TcpProxyConfiguration.() -> Unit) {
        this.proxyConfiguration = TcpProxyConfiguration().also { it.configurationBlock() }
    }

}
