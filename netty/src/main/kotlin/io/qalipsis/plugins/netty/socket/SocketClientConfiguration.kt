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

package io.qalipsis.plugins.netty.socket

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.plugins.netty.configuration.ConnectionConfiguration
import io.qalipsis.plugins.netty.configuration.TlsConfiguration
import java.time.Duration
import javax.validation.Valid

/**
 *
 * Configuration to connect to a remote peer using a socket.
 *
 * @property connectTimeout time out to establish a connection, defaults to 10 seconds
 * @property keepConnectionAlive keep the connection open even if there is no other step using it (this is required when the connection is used between different operations)
 * @property noDelay disables the Nagle's algorithm when set to `true`, defaults to false
 * @property tlsConfiguration when set, enables and configures TLS to connect to the remote peer
 *
 * @author Eric Jessé
 */
@Spec
abstract class SocketClientConfiguration internal constructor(
    @field:PositiveDuration var connectTimeout: Duration = Duration.ofSeconds(10),
    var keepConnectionAlive: Boolean = false,
    var noDelay: Boolean = true,
    @field:Valid internal var tlsConfiguration: TlsConfiguration? = null
) : ConnectionConfiguration() {

    /**
     * Enables and configures the connection to the remote peer with TLS.
     */
    fun tls(configurationBlock: TlsConfiguration.() -> Unit) {
        this.tlsConfiguration = TlsConfiguration()
            .also { it.configurationBlock() }
    }
}
