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
