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
 * Default implementation of the [HttpResponse].
 *
 * @author Eric Jessé
 */
internal class DefaultHttpResponse<B>(
    override val status: HttpResponseStatus,
    override val contentType: MediaType?,
    override val headers: Map<String, String>,
    override val cookies: Map<String, Cookie>,
    override val bodyBytes: ByteArray?,
    override val body: B?,
) : HttpResponse<B>
