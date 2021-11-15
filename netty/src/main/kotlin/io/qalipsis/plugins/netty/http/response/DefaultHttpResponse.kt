package io.qalipsis.plugins.netty.http.response

import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.cookie.Cookie

/**
 * Default implementation of the [HttpResponse].
 *
 * @author Eric Jessé
 */
internal class DefaultHttpResponse<B>(
    override val status: HttpResponseStatus,
    override val contentType: MediaType?,
    override val headers: Map<String, String>,
    override val cookies: Map<String, Cookie>,
    override val bodyBytes: ByteArray?,
    override val body: B?,
) : HttpResponse<B>
