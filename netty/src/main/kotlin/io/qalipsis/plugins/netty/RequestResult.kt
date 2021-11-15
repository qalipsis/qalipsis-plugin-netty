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
