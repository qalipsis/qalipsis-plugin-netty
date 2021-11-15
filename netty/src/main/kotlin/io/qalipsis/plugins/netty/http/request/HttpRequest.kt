package io.qalipsis.plugins.netty.http.request

import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.cookie.Cookie

/**
 * Description of a HTTP request.
 *
 * @author Eric Jessé
 */
interface HttpRequest<SELF : HttpRequest<SELF>> {

    /**
     * URI of the request.
     */
    val uri: String

    /**
     * Immutable method of the request.
     */
    val method: HttpMethod

    /**
     * Mutable headers of the request.
     */
    val headers: MutableMap<CharSequence, Any>

    /**
     * Mutable parameters of the request, as set in the URL.
     */
    val parameters: MutableMap<CharSequence, MutableCollection<CharSequence>>

    fun addHeader(name: CharSequence, value: Any): SELF {
        headers[name] = value
        @Suppress("UNCHECKED_CAST")
        return this as SELF
    }

    fun addParameter(name: CharSequence, value: CharSequence): SELF {
        parameters.computeIfAbsent(name) { mutableListOf() }.add(value)
        @Suppress("UNCHECKED_CAST")
        return this as SELF
    }

    fun addCookies(vararg cookie: Cookie): SELF

}
