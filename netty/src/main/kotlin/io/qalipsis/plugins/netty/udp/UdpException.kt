package io.qalipsis.plugins.netty.udp

/**
 * Exception of a UDP step containing the full details.
 *
 * @author Eric Jessé
 */
data class UdpException(val result: UdpResult<*, *>) : RuntimeException(result.cause)
