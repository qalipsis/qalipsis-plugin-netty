package io.qalipsis.plugins.netty.http.response

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.micronaut.jackson.modules.BeanIntrospectionModule
import io.qalipsis.api.annotations.PluginComponent
import kotlin.reflect.KClass

/**
 * Implementation of [HttpBodyDeserializer] to deserialize XML payloads.
 *
 * @author Eric Jessé
 */
@PluginComponent
internal class XmlHttpBodyDeserializer : HttpBodyDeserializer {

    private val mapper = XmlMapper().apply {
        registerModule(BeanIntrospectionModule())
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        registerModule(Jdk8Module())
    }

    private val mediaTypes: Collection<MediaType> = listOf(
        MediaType.APPLICATION_XML_TYPE,
        MediaType.TEXT_XML_TYPE
    )

    override val order: Int = 10_001

    override fun accept(mediaType: MediaType) = mediaTypes.any { it.matches(mediaType) }

    override fun <B : Any> convert(content: ByteArray, mediaType: MediaType, type: KClass<B>): B? {
        return mapper.readValue(content, type.java)
    }
}
