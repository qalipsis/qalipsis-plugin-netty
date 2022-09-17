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

import kotlin.reflect.KClass

interface HttpBodyDeserializer {

    /**
     * Verifies if the deserializer is relevant of the provided [MediaType].
     */
    fun accept(mediaType: MediaType): Boolean

    /**
     * Converts the payload of a response with a type in [mediaTypes] into an instance of [B].
     */
    fun <B : Any> convert(content: ByteArray, mediaType: MediaType, type: KClass<B>): B?

    /**
     * Order of the deserializer for the lookup. The lowest the value, the earliest in the lookup.
     */
    val order: Int
}
