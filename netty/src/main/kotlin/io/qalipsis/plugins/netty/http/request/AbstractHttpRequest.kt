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
        configuration: HttpClientConfiguration
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
