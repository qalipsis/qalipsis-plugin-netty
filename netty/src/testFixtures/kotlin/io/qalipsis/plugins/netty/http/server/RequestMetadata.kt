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

package io.qalipsis.plugins.netty.http.server

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.util.ReferenceCountUtil
import io.qalipsis.api.logging.LoggerHelper.logger

data class RequestMetadata(
    val uri: String? = null,
    val path: String? = null,
    val version: String? = null,
    val method: String? = null,
    val parameters: MutableMap<String, MutableList<String>> = mutableMapOf(),
    val headers: MutableMap<String, String> = mutableMapOf(),
    val cookies: MutableMap<String, String> = mutableMapOf(),
    var multipart: Boolean = false,
    var files: MutableMap<String, MutableList<FileMetadata>> = mutableMapOf(),
    var size: Int = 0,
    var data: String? = null,
    var form: MutableMap<String, MutableList<String>> = mutableMapOf()
) {

    fun toJson(): String = objectMapper.writeValueAsString(this)

    companion object {

        @JvmStatic
        private val objectMapper = ObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }

        @JvmStatic
        fun parse(content: ByteBuf): RequestMetadata {
            ReferenceCountUtil.retain(content)
            val copiedContent = Unpooled.copiedBuffer(content)
            ReferenceCountUtil.release(content)
            val data = copiedContent.array()
            log.info { "request description to parse: ${data.toString(Charsets.UTF_8)}" }
            return objectMapper.readValue(data, RequestMetadata::class.java)
        }

        @JvmStatic
        private val log = logger()
    }
}

data class FileMetadata(val name: String? = null, val contentType: String? = null, val size: Int? = null)
