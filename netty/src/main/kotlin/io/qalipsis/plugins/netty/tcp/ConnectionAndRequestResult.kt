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
