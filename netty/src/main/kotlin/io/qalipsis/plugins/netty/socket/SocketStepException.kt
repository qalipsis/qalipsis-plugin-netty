package io.qalipsis.plugins.netty.socket

import io.qalipsis.plugins.netty.tcp.ConnectionAndRequestResult

/**
 * Exception of a step using a TCP connection and containing the full details.
 *
 * @author Eric Jessé
 */
class SocketStepException(val result: ConnectionAndRequestResult<*, *>) : RuntimeException(result.cause)
