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

import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpStatusClass
import io.netty.handler.codec.http.cookie.ClientCookieDecoder
import io.netty.handler.codec.http.cookie.Cookie
import io.qalipsis.api.logging.LoggerHelper.logger
import kotlin.reflect.KClass
import io.netty.handler.codec.http.HttpResponse as NettyResponse

/**
 * Converter from Netty to Qalipsis HTTP response.
 *
 * @author Eric Jessé
 */
internal class ResponseConverter<B>(
    private val argumentType: KClass<*>,
    private val deserializers: List<HttpBodyDeserializer>
) {

    private val cookieDecoder = ClientCookieDecoder.LAX

    /**
     * Converts a Netty [FullHttpResponse] into a Qalipsis [HttpResponse] where the body can be deserialized.
     */
    fun convert(nettyResponse: NettyResponse): HttpResponse<B> {
        val headers = mutableMapOf<String, String>()
        val cookies = mutableMapOf<String, Cookie>()

        return (nettyResponse as? FullHttpResponse)?.let {
            fillHeadersAndCookies(it, cookies, headers)

            val responseType = headers.filterKeys { it.lowercase() == CONTENT_TYPE_HEADER }.values.firstOrNull()
                ?.let { MediaType(it) }
            val usedContentType = responseType ?: MediaType.TEXT_PLAIN_TYPE

            val bytes = ByteBufUtil.getBytes(nettyResponse.content())
            nettyResponse.release()
            DefaultHttpResponse(
                nettyResponse.status(),
                responseType,
                headers,
                cookies,
                bytes,
                convertBody(nettyResponse, bytes, usedContentType)
            )
        } ?: DefaultHttpResponse(
            nettyResponse.status(),
            null,
            headers,
            cookies,
            null,
            null,
        )
    }

    /**
     * Converts the body to the expected type if the response is successful, null otherwise.
     */
    @Suppress("UNCHECKED_CAST", "IMPLICIT_CAST_TO_ANY")
    private fun convertBody(
        nettyResponse: FullHttpResponse,
        bytes: ByteArray,
        usedContentType: MediaType
    ) = if (nettyResponse.status().codeClass() == HttpStatusClass.SUCCESS) {
        when (argumentType) {
            Unit::class -> Unit
            String::class -> String(bytes, usedContentType.charset)
            ByteArray::class -> bytes
            else -> deserialize(bytes, usedContentType)
        }
    } else {
        log.trace { "Body converted to null because of non successful HTTP status" }
        null
    } as B?

    private fun fillHeadersAndCookies(
        nettyResponse: FullHttpResponse,
        cookies: MutableMap<String, Cookie>,
        headers: MutableMap<String, String>
    ) {
        nettyResponse.headers().asSequence().forEach { (key, value) ->
            if (key.lowercase() == COOKIE_HEADER) {
                val cookie = cookieDecoder.decode(value)
                log.trace { "Received cookie $cookie" }
                cookies[cookie.name()] = cookie
            } else {
                log.trace { "Received header $key: $value" }
                headers[key.lowercase()] = value
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun deserialize(bytes: ByteArray, responseType: MediaType): B? {
        log.trace { "Search the converters for the media type $responseType" }
        return deserializers
            // All the matching converters are tried until one is working.
            .asSequence()
            .filter { it.accept(responseType) }
            .mapNotNull { deserializer ->
                kotlin.runCatching {
                    log.trace { "Trying to convert the response body with the deserializer ${deserializer::class}" }
                    deserializer.convert(bytes, responseType, argumentType).apply {
                        log.trace { "Response body successfully converted with the deserializer ${deserializer::class}" }
                    }
                }.getOrNull()
            }
            .firstOrNull() as B?
    }

    companion object {

        private const val COOKIE_HEADER = "set-cookie"

        private const val CONTENT_TYPE_HEADER = "content-type"

        private val log = logger()

    }
}
