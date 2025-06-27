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
