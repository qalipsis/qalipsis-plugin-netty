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
