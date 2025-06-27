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
