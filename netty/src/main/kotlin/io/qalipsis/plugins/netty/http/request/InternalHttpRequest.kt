package io.qalipsis.plugins.netty.http.request

import io.netty.handler.codec.http.HttpMethod
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration

/**
 * Description of a request as seen internally.
 */
internal interface InternalHttpRequest<SELF : HttpRequest<SELF>, NETTY_QUERY : Any> : HttpRequest<SELF> {

    /**
     * Duplicates the request using a different URI and HTTP method if set.
     */
    fun with(uri: String?, method: HttpMethod? = null): SELF

    /**
     * Converts the request to a native Netty request.
     */
    fun toNettyRequest(clientConfiguration: HttpClientConfiguration): NETTY_QUERY

    /**
     * Computes the complete URI for the coming request.
     */
    fun computeUri(clientConfiguration: HttpClientConfiguration): String

}
