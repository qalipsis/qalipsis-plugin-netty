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
