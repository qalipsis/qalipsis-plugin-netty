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

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringEncoder
import io.netty.handler.codec.http.cookie.ClientCookieEncoder
import io.netty.handler.codec.http.cookie.Cookie
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder
import io.netty.handler.codec.http.multipart.InterfaceHttpData
import io.netty.handler.codec.http.multipart.MemoryFileUpload
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import java.io.File
import java.nio.charset.Charset

/**
 * Request containing a multi-part body.
 *
 * @author Eric Jessé
 */
data class FormOrMultipartHttpRequest(
    override val method: HttpMethod,
    override val uri: String,
    private val multipart: Boolean = false
) : AbstractHttpRequest<FormOrMultipartHttpRequest>(),
    InternalHttpRequest<FormOrMultipartHttpRequest, HttpPostRequestEncoder> {

    override val headers = mutableMapOf<CharSequence, Any>()

    override val parameters = mutableMapOf<CharSequence, MutableCollection<CharSequence>>()

    private val cookies = mutableListOf<Cookie>()

    private val parts = mutableListOf<Part>()

    /**
     * Adds a simple attribute in the body as [name]=[value].
     */
    fun addAttribute(name: String, value: String): FormOrMultipartHttpRequest {
        parts.add(AttributePart(name, value))
        return this
    }

    /**
     * Adds a file to upload in the body.
     *
     * @param name the name of the parameter
     * @param file the file to be uploaded
     * @param contentType the associated contentType for the file
     * @param isText if this file should be transmitted in Text format (else binary)
     *
     */
    fun addFileUpload(
        name: String,
        file: File,
        contentType: String,
        isText: Boolean
    ): FormOrMultipartHttpRequest {
        return addFileUpload(name, file.name, file, contentType, isText)
    }

    /**
     * Adds a file to upload in the body.
     *
     * @param name the name of the parameter
     * @param filename the name of the file
     * @param file the file to be uploaded
     * @param contentType the associated contentType for the file
     * @param isText if this file should be transmitted in Text format (else binary)
     *
     */
    fun addFileUpload(
        name: String,
        filename: String,
        file: File,
        contentType: String,
        isText: Boolean
    ): FormOrMultipartHttpRequest {
        parts.add(FileUploadPart(name, filename, file, contentType, isText))
        return this
    }

    /**
     * Adds a memory file to upload in the body.
     *
     * @param name the name of the parameter
     * @param filename the name of the file
     * @param content the content of the file
     * @param contentTransferEncoding content-Transfer-Encoding type from String as 7bit, 8bit or binary, defaults to 7bit
     * @param contentType the associated contentType for the file
     * @param charset charset of the file
     *
     */
    fun addFileUpload(
        name: String,
        filename: String,
        content: ByteArray,
        contentTransferEncoding: TransferEncodingMechanism = TransferEncodingMechanism.BIT7,
        contentType: String,
        charset: Charset
    ): FormOrMultipartHttpRequest {
        val memoryFileUpload =
            MemoryFileUpload(
                name,
                filename,
                contentType,
                contentTransferEncoding.value,
                charset,
                content.size.toLong()
            )
        memoryFileUpload.setContent(Unpooled.copiedBuffer(content))
        addHttpData(memoryFileUpload)

        return this
    }

    /**
     * Add the Netty [InterfaceHttpData] to the Body list
     */
    fun addHttpData(data: InterfaceHttpData): FormOrMultipartHttpRequest {
        parts.add(HttpDataPart(data))
        return this
    }

    override fun addCookies(vararg cookie: Cookie): FormOrMultipartHttpRequest {
        cookies.addAll(cookie.toList())
        return this
    }

    override fun with(uri: String?, method: HttpMethod?): FormOrMultipartHttpRequest {
        return this.copy(uri = uri ?: this.uri, method = method ?: this.method)
    }

    override fun computeUri(clientConfiguration: HttpClientConfiguration): String {
        return getCompleteUri(clientConfiguration)
    }

    override fun toNettyRequest(clientConfiguration: HttpClientConfiguration): HttpPostRequestEncoder {
        val queryEncoder = QueryStringEncoder(getCompleteUri(clientConfiguration))
        parameters.forEach { (name, values) ->
            values.forEach { value ->
                queryEncoder.addParam(
                    name.toString(),
                    value.toString()
                )
            }
        }
        val request = DefaultFullHttpRequest(clientConfiguration.version.nettyVersion, method, queryEncoder.toString())
        headers.forEach { (name, value) -> request.headers().set(name, value) }
        if (cookies.isNotEmpty()) {
            request.headers().add(HttpHeaderNames.COOKIE, ClientCookieEncoder.LAX.encode(cookies))
        }
        completeRequest(request, clientConfiguration)

        val factory = DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE)
        val bodyRequestEncoder = HttpPostRequestEncoder(factory, request, multipart)
        parts.forEach { it.addToRequestEncoder(bodyRequestEncoder) }
        return bodyRequestEncoder
    }

    /**
     * Part of the multipart request.
     */
    private interface Part {

        fun addToRequestEncoder(encoder: HttpPostRequestEncoder)
    }

    /**
     * Simple attribute in the body as [name]=[value].
     */
    private class AttributePart(private val name: String, private val value: String) : Part {
        override fun addToRequestEncoder(encoder: HttpPostRequestEncoder) {
            encoder.addBodyAttribute(name, value)
        }
    }

    /**
     * File to upload.
     */
    private class FileUploadPart(
        private val name: String,
        private val filename: String,
        private val file: File,
        private val contentType: String,
        private val isText: Boolean
    ) : Part {
        override fun addToRequestEncoder(encoder: HttpPostRequestEncoder) {
            encoder.addBodyFileUpload(name, filename, file, contentType, isText)
        }
    }

    /**
     * Wrapper for Netty [InterfaceHttpData] parts of the body.
     */
    private class HttpDataPart(private val data: InterfaceHttpData) : Part {
        override fun addToRequestEncoder(encoder: HttpPostRequestEncoder) {
            encoder.addBodyHttpData(data)
        }
    }
}
