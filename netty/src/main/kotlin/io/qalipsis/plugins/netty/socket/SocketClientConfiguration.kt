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
