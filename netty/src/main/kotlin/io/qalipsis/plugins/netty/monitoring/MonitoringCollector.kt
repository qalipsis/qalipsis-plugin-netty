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
