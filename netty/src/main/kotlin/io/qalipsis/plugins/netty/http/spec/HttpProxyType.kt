package io.qalipsis.plugins.netty.http.spec

import io.qalipsis.api.annotations.Spec


/**
 * Supported protocols for HTTP connections.
 *
 * @author Eric Jessé
 */
@Spec
enum class HttpProxyType {
    SOCKS5, HTTP
}
