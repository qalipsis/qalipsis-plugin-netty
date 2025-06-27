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

package io.qalipsis.plugins.netty.tcp

import io.qalipsis.plugins.netty.RequestResult
import java.time.Duration

/**
 * Result of a TCP step with connection.
 */
class ConnectionAndRequestResult<I, O>(
    val connected: Boolean,
    val connectionFailure: Throwable?,
    val tlsFailure: Throwable?,
    sendingFailure: Throwable?,
    failure: Throwable?,
    input: I,
    response: O?,
    meters: Meters
) : RequestResult<I, O, ConnectionAndRequestResult.Meters>(
    sendingFailure, failure, input, response, meters
) {

    override val cause = failure ?: connectionFailure ?: tlsFailure ?: sendingFailure

    override val isFailure = (cause != null)

    override val isSuccess = !isFailure

    interface Meters : RequestResult.Meters {
        val timeToSuccessfulConnect: Duration?
        val timeToFailedConnect: Duration?
        val timeToSuccessfulTlsConnect: Duration?
        val timeToFailedTlsConnect: Duration?
    }

    internal data class MetersImpl(
        override var timeToSuccessfulConnect: Duration? = null,
        override var timeToFailedConnect: Duration? = null,
        override var timeToSuccessfulTlsConnect: Duration? = null,
        override var timeToFailedTlsConnect: Duration? = null,
        override var bytesCountToSend: Int = 0,
        override var sentBytes: Int = 0,
        override var timeToFirstByte: Duration? = null,
        override var timeToLastByte: Duration? = null,
        override var receivedBytes: Int = 0
    ) : Meters
}
