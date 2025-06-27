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

package io.qalipsis.plugins.netty.monitoring

import java.time.Duration

internal interface MonitoringCollector {

    fun recordConnecting() = Unit

    fun recordConnected(timeToConnect: Duration) = Unit

    fun recordConnectionFailure(timeToFailure: Duration, throwable: Throwable) = Unit

    fun recordTlsHandshakeSuccess(timeToConnect: Duration) = Unit

    fun recordTlsHandshakeFailure(timeToFailure: Duration, throwable: Throwable) = Unit

    fun recordSendingRequest()

    fun recordSendingData(bytesCount: Int)

    fun recordSentDataSuccess(bytesCount: Int)

    fun recordSentDataFailure(throwable: Throwable)

    fun recordSentRequestSuccess()

    fun recordSentRequestFailure(throwable: Throwable)

    fun recordReceivingData()

    fun countReceivedData(bytesCount: Int)

    fun recordReceivingDataFailure(throwable: Throwable)

    fun recordReceptionComplete()
}
