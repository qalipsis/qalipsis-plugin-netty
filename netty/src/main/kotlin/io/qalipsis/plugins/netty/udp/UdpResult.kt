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

package io.qalipsis.plugins.netty.udp

import io.qalipsis.plugins.netty.RequestResult
import java.time.Duration

/**
 * Result of a UDP request.
 */
data class UdpResult<I, O>(
    val sendingFailure: Throwable?,
    val failure: Throwable?,
    val input: I,
    val response: O?,
    val meters: RequestResult.Meters
) {

    val cause = failure ?: sendingFailure

    val isFailure = (cause != null)

    val isSuccess = !isFailure

    internal data class MetersImpl(
        override var bytesCountToSend: Int = 0,
        override var sentBytes: Int = 0,
        override var timeToFirstByte: Duration? = null,
        override var timeToLastByte: Duration? = null,
        override var receivedBytes: Int = 0
    ) : RequestResult.Meters
}
