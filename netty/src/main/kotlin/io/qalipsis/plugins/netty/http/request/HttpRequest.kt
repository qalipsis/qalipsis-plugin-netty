/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
