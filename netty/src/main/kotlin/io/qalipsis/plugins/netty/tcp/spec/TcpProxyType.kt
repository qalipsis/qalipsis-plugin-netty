package io.qalipsis.plugins.netty.tcp.spec

import io.qalipsis.api.annotations.Spec


/**
 * Supported protocols for TCP connections.
 *
 * @author Eric Jessé
 */
@Spec
enum class TcpProxyType {
    SOCKS4, SOCKS5
}
