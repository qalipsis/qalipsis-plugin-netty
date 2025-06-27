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
