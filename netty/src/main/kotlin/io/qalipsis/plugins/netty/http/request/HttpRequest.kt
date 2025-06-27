/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.plugins.netty.http.request

import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.cookie.Cookie
import java.nio.charset.StandardCharsets
import java.util.Base64

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

    /**
     * Adds a basic authentication header to the request.
     */
    fun withBasicAuth(username: String, password: String): SELF {
        headers[HttpHeaderNames.AUTHORIZATION] =
            "Basic ${BASE_64_ENCODER.encodeToString(("$username:$password").toByteArray(StandardCharsets.UTF_8))}"
        @Suppress("UNCHECKED_CAST")
        return this as SELF
    }

    private companion object {

        val BASE_64_ENCODER: Base64.Encoder = Base64.getEncoder()
    }
}
