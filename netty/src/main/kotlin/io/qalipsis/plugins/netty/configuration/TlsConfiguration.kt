package io.qalipsis.plugins.netty.configuration

import io.netty.handler.ssl.ClientAuth
import io.qalipsis.api.annotations.Spec

/**
 * Configures the behavior of the TLS handler.
 *
 * @property disableCertificateVerification when set to true, the remote certificate is not validated during the hand-shake, default to false
 *
 * @author Eric Jessé
 */
@Spec
data class TlsConfiguration internal constructor(
    var disableCertificateVerification: Boolean = false
) {
    internal var protocols = arrayOf<String>()

    internal var ciphers = arrayOf<String>()

    internal var clientAuthentication: ClientAuth? = null

    /**
     * TLS protocols to support, instead of the default ones from the TLS Netty handler.
     */
    fun protocols(vararg protocols: String) {
        this.protocols += protocols
    }

    /**
     * TLS ciphers to support, instead of the default ones from the TLS Netty handler.
     */
    fun ciphers(vararg ciphers: String) {
        this.ciphers += ciphers
    }

}
