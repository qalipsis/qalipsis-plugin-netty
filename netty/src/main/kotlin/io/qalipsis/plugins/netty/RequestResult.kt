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
