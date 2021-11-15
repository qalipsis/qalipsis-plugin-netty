package io.qalipsis.plugins.netty.http.response

import java.nio.charset.Charset

/**
 * Represents a media type.
 *
 * See (https://www.iana.org/assignments/media-types/media-types.xhtml and https://tools.ietf.org/html/rfc2046).
 *
 * @author Eric Jessé
 */
data class MediaType(
    val type: String,
    val subtype: String,
    val charset: Charset = HTTP_DEFAULT_CHARSET,
    val extension: String? = null
) {

    constructor(value: String, extension: String? = null) : this(
        value.substringBefore(";").substringBefore("/").trim().lowercase().takeIf { it.isNotEmpty() } ?: "*",
        value.substringBefore(";").substringAfter("/").trim().lowercase().takeIf { it.isNotEmpty() } ?: "*",
        value.takeIf { it.contains("charset=") }?.substringAfter("charset=")?.substringBefore(";")?.trim()
            ?.takeIf { it.isNotBlank() }?.let { Charset.forName(it) } ?: HTTP_DEFAULT_CHARSET,
        extension?.lowercase()
    )

    /**
     * Checks if this [MediaType] is a parent or equal to [otherMediaType].
     */
    fun matches(otherMediaType: MediaType): Boolean {
        val typeMatch = type == "*" || type.equals(otherMediaType.type, ignoreCase = true)
        val subtypeMatch = subtype == "*" || subtype.equals(otherMediaType.subtype, ignoreCase = true)
        return typeMatch && subtypeMatch
    }

    companion object {

        @JvmStatic
        private val HTTP_DEFAULT_CHARSET = Charsets.ISO_8859_1

        const val TEXT_PLAIN = "text/plain"

        @JvmStatic
        val TEXT_PLAIN_TYPE = MediaType(TEXT_PLAIN)

        const val TEXT_HTML = "text/html"

        @JvmStatic
        val TEXT_HTML_TYPE = MediaType(TEXT_HTML)

        const val TEXT_XML = "text/xml"

        @JvmStatic
        val TEXT_XML_TYPE = MediaType(TEXT_XML)

        const val TEXT_JSON = "text/json"

        @JvmStatic
        val TEXT_JSON_TYPE = MediaType(TEXT_JSON)

        const val TEXT_CSS = "text/css"

        @JvmStatic
        val TEXT_CSS_TYPE = MediaType(TEXT_CSS)

        const val TEXT_JAVASCRIPT = "text/javascript"

        @JvmStatic
        val TEXT_JAVASCRIPT_TYPE = MediaType(TEXT_JAVASCRIPT)

        const val APPLICATION_JAVASCRIPT = "application/javascript"

        @JvmStatic
        val APPLICATION_JAVASCRIPT_TYPE = MediaType(APPLICATION_JAVASCRIPT)

        const val APPLICATION_XHTML = "application/xhtml+xml"

        @JvmStatic
        val APPLICATION_XHTML_TYPE = MediaType(APPLICATION_XHTML)

        const val APPLICATION_XML = "application/xml"

        @JvmStatic
        val APPLICATION_XML_TYPE = MediaType(APPLICATION_XML)

        const val APPLICATION_JSON = "application/json"

        @JvmStatic
        val APPLICATION_JSON_TYPE = MediaType(APPLICATION_JSON)

        const val APPLICATION_YAML = "application/x-yaml"

        @JvmStatic
        val APPLICATION_YAML_TYPE = MediaType(APPLICATION_YAML)

        const val APPLICATION_OCTET_STREAM = "application/octet-stream"

        @JvmStatic
        val APPLICATION_OCTET_STREAM_TYPE = MediaType(APPLICATION_OCTET_STREAM)

    }
}
