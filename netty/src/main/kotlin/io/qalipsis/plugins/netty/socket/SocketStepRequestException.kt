package io.qalipsis.plugins.netty.socket

import io.qalipsis.plugins.netty.RequestResult

/**
 * Exception of a step using a TCP connection and containing the details of the query and failures.
 *
 * @author Eric Jessé
 */
data class SocketStepRequestException(val result: RequestResult<*, *, *>) : RuntimeException(result.cause)
