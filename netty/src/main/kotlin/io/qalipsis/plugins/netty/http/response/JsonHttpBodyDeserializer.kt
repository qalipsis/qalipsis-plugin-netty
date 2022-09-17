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

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
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
        addModule(kotlinModule())
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
