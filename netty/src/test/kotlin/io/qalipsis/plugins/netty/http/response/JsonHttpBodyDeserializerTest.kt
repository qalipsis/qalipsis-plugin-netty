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

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isNotNull
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class JsonHttpBodyDeserializerTest {

    private val deserializer = JsonHttpBodyDeserializer()

    @Test
    internal fun `should match the JSON payloads`() {
        assertTrue(deserializer.accept(MediaType("${MediaType.TEXT_JSON};charset=UTF-8")))
        assertTrue(deserializer.accept(MediaType("${MediaType.APPLICATION_JSON};charset=UTF-8")))
        assertTrue(deserializer.accept(MediaType("${MediaType.TEXT_JAVASCRIPT};charset=UTF-8")))
        assertTrue(deserializer.accept(MediaType("${MediaType.APPLICATION_JAVASCRIPT};charset=UTF-8")))
    }

    @Test
    internal fun `should not match non JSON payloads`() {
        assertFalse(deserializer.accept(relaxedMockk()))
    }

    @Test
    internal fun `should convert the JSON payload`() {
        val jsonPayload = """{"field": "value"}""".toByteArray()
        val result = deserializer.convert(jsonPayload, relaxedMockk(), Entity::class)

        assertThat(result).isNotNull().isDataClassEqualTo(Entity("value"))
    }

    private data class Entity(val field: String)
}
