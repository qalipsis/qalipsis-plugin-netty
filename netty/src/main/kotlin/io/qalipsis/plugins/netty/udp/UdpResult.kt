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
