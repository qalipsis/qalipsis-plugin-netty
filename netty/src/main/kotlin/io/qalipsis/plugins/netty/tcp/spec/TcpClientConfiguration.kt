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
