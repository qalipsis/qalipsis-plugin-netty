package io.qalipsis.plugins.netty.tcp

import io.qalipsis.plugins.netty.socket.SocketClientStep

/**
 * General interface for the TCP steps.
 *
 * @author Eric Jessé
 */
internal interface TcpClientStep<I, R> : SocketClientStep<I, ByteArray, ByteArray, R>
