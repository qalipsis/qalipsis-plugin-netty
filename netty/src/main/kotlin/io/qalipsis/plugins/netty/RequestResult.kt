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

package io.qalipsis.plugins.netty

import java.time.Duration

/**
 * Result of a query step.
 */
open class RequestResult<I, O, M : RequestResult.Meters>(
    val sendingFailure: Throwable?,
    val failure: Throwable?,
    val input: I,
    val response: O?,
    val meters: M
) {

    open val cause = failure ?: sendingFailure

    open val isFailure = cause != null

    open val isSuccess = !isFailure

    interface Meters {
        val bytesCountToSend: Int
        val sentBytes: Int
        val timeToFirstByte: Duration?
        val timeToLastByte: Duration?
        val receivedBytes: Int
    }

}
