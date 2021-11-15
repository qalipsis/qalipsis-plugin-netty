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
