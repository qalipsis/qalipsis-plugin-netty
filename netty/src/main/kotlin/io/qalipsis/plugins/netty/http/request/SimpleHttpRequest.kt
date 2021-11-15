package io.qalipsis.plugins.netty.http.request

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringEncoder
import io.netty.handler.codec.http.cookie.ClientCookieEncoder
import io.netty.handler.codec.http.cookie.Cookie
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration

/**
 * Standard HTTP Request.
 *
 * @see FormOrMultipartHttpRequest
 *
 * @author Eric Jessé
 */
data class SimpleHttpRequest constructor(
    override val method: HttpMethod,
    override val uri: String
) : AbstractHttpRequest<SimpleHttpRequest>(),
    InternalHttpRequest<SimpleHttpRequest, io.netty.handler.codec.http.HttpRequest> {

    private var bodyBytes: ByteArray = ByteArray(0)

    private var bodyString: CharSequence? = null

    private var contentType: CharSequence? = null

    override val headers = mutableMapOf<CharSequence, Any>()

    override val parameters = mutableMapOf<CharSequence, MutableCollection<CharSequence>>()

    private val cookies = mutableListOf<Cookie>()

    /**
     * Sets the [String] as body of the request. If [contentType] does not contain the charset, the charset
     * defined in the configuration of HTTP step will be added.
     *
     * @param body string to set as body of the request
     * @param contentType value for the content type header, not set if omitted.
     */
    fun body(body: CharSequence, contentType: CharSequence? = null): SimpleHttpRequest {
        bodyString = body
        bodyBytes = ByteArray(0)
        this.contentType = contentType
        return this
    }

    /**
     * Sets the [ByteArray] as body of the request. If [contentType] does not contain the charset, the charset
     * defined in the configuration of HTTP step will be added.
     *
     * @param body byte array to set as body of the request
     * @param contentType value for the content type header, not set if omitted.
     */
    fun body(body: ByteArray, contentType: CharSequence? = null): SimpleHttpRequest {
        bodyBytes = body.clone()
        bodyString = null
        this.contentType = contentType
        return this
    }

    /**
     * Sets the [ByteBuf] as body of the request. If [contentType] does not contain the charset, the charset
     * defined in the configuration of HTTP step will be added.
     *
     * @param body byte buffer to copy as body of the request
     * @param contentType value for the content type header, not set if omitted.
     */
    fun body(body: ByteBuf, contentType: CharSequence? = null): SimpleHttpRequest {
        bodyBytes = body.array().clone()
        bodyString = null
        this.contentType = contentType
        return this
    }

    override fun addCookies(vararg cookie: Cookie): SimpleHttpRequest {
        cookies.addAll(cookie.toList())
        return this
    }

    /**
     * Defines the content type of the body. If [contentType] does not contain the charset, the charset
     * defined in the configuration of HTTP step will be added.
     */
    fun contentType(contentType: CharSequence): SimpleHttpRequest {
        this.contentType = contentType
        return this
    }

    override fun with(uri: String?, method: HttpMethod?): SimpleHttpRequest {
        val clone = this.copy(uri = uri ?: this.uri, method = method ?: this.method)
        clone.bodyBytes = clone.bodyBytes.clone()
        return clone
    }

    override fun computeUri(clientConfiguration: HttpClientConfiguration): String {
        return getCompleteUri(clientConfiguration)
    }

    override fun toNettyRequest(clientConfiguration: HttpClientConfiguration): io.netty.handler.codec.http.HttpRequest {

        val queryEncoder = QueryStringEncoder(getCompleteUri(clientConfiguration))
        parameters.forEach { (name, values) ->
            values.forEach { value ->
                queryEncoder.addParam(
                    name.toString(),
                    value.toString()
                )
            }
        }
        this.contentType?.also { contentType ->
            val valueWithEncoding = if (contentType.endsWith("charset=${clientConfiguration.charset.name()}")) {
                contentType
            } else {
                "$contentType; charset=${clientConfiguration.charset.name()}"
            }
            addHeader(HttpHeaderNames.CONTENT_TYPE, valueWithEncoding)
        }
        val bodyBuffer = bodyString?.let { Unpooled.copiedBuffer(it, clientConfiguration.charset) }
            ?: if (bodyBytes.isNotEmpty()) Unpooled.copiedBuffer(bodyBytes) else Unpooled.EMPTY_BUFFER

        if (!headers.containsKey(HttpHeaderNames.CONTENT_LENGTH)) {
            addHeader(HttpHeaderNames.CONTENT_LENGTH, bodyBuffer.readableBytes())
        }
        val request =
            DefaultFullHttpRequest(
                clientConfiguration.version.nettyVersion,
                method,
                queryEncoder.toString(),
                bodyBuffer
            )
        headers.forEach { (name, value) -> request.headers().set(name, value) }
        if (cookies.isNotEmpty()) {
            request.headers().add(HttpHeaderNames.COOKIE, ClientCookieEncoder.LAX.encode(cookies))
        }

        completeRequest(request, clientConfiguration)

        return request
    }

}
