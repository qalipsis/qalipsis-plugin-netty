package io.qalipsis.plugins.netty.http.response

import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.cookie.Cookie

/**
 * Common interface for the HTTP responses.
 *
 * @author Eric Jessé
 */
interface HttpResponse<B> {

    /**
     * Current status.
     */
    val status: HttpResponseStatus

    /**
     * Type of the content, if set.
     */
    val contentType: MediaType?

    /**
     * Headers, excluding the cookies.
     */
    val headers: Map<String, String>

    /**
     * Cookies returned by the server.
     */
    val cookies: Map<String, Cookie>

    /**
     * Body of the response converted to a [B].
     */
    val body: B?

    /**
     * Body of the response as a [ByteArray].
     */
    val bodyBytes: ByteArray?

}
