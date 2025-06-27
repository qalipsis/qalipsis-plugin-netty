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

package io.qalipsis.plugins.netty.http.response

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.micronaut.jackson.modules.BeanIntrospectionModule
import io.qalipsis.api.annotations.PluginComponent
import kotlin.reflect.KClass

/**
 * Implementation of [HttpBodyDeserializer] to deserialize JSON payloads.
 *
 * @author Eric Jessé
 */
@PluginComponent
internal class JsonHttpBodyDeserializer : HttpBodyDeserializer {

    private val mapper = jsonMapper {
        addModule(KotlinModule.Builder().build())
        addModule(Jdk8Module())
        addModule(JavaTimeModule())
        addModule(BeanIntrospectionModule())

        enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
        visibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
    }

    private val mediaTypes: Collection<MediaType> = listOf(
        MediaType.APPLICATION_JSON_TYPE,
        MediaType.TEXT_JSON_TYPE,
        MediaType.APPLICATION_JAVASCRIPT_TYPE,
        MediaType.TEXT_JAVASCRIPT_TYPE
    )

    override val order: Int = 10_000

    override fun accept(mediaType: MediaType) = mediaTypes.any { it.matches(mediaType) }

    override fun <B : Any> convert(content: ByteArray, mediaType: MediaType, type: KClass<B>): B? {
        return mapper.readValue(content, type.java)
    }
}
