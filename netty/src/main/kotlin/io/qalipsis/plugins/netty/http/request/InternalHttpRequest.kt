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
