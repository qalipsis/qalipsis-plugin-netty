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
import io.netty.handler.codec.http.HttpHeaderValues
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import java.net.URI

abstract class AbstractHttpRequest<SELF : HttpRequest<SELF>> : HttpRequest<SELF> {

    private var fullUri: String? = null

    protected fun getCompleteUri(configuration: HttpClientConfiguration): String {
        return fullUri ?: doComputeUri(configuration)
    }

    private fun doComputeUri(configuration: HttpClientConfiguration): String {
        var processedUri = uri
        if (!processedUri.startsWith(configuration.scheme)) {
            val configurationHostAndPort = "${configuration.host}:${configuration.port}"
            if (!processedUri.startsWith(configurationHostAndPort)) {
                processedUri = if (processedUri.startsWith("/")) {
                    "${configurationHostAndPort}${configuration.contextPath}${processedUri}"
                } else {
                    "${configurationHostAndPort}${configuration.contextPath}/${processedUri}"
                }
            }
            processedUri = "${configuration.scheme}://${processedUri}"
        }
        fullUri = processedUri
        return processedUri
    }

    /**
     * Completes the details of the request to make sure it contains all the required elements for the routing.
     */
    protected fun completeRequest(
        request: io.netty.handler.codec.http.HttpRequest,
        configuration: HttpClientConfiguration,
    ) {
        if (request.headers()[HttpHeaderNames.CONNECTION].isNullOrBlank() && configuration.keepConnectionAlive) {
            request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
        }
        if (request.headers()[HttpHeaderNames.HOST].isNullOrBlank()) {
            val uri = URI(request.uri())
            request.headers().set(HttpHeaderNames.HOST, "${uri.host}:${uri.port}")
        }
        if (configuration.inflate && request.headers()[HttpHeaderNames.ACCEPT_ENCODING].isNullOrBlank()) {
            request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP)
        }
    }
}
