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

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isNotNull
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class XmlHttpBodyDeserializerTest {

    private val deserializer = XmlHttpBodyDeserializer()

    @Test
    internal fun `should match the XML payloads`() {
        assertTrue(deserializer.accept(MediaType("${MediaType.TEXT_XML};charset=UTF-8")))
        assertTrue(deserializer.accept(MediaType("${MediaType.APPLICATION_XML};charset=UTF-8")))
    }

    @Test
    internal fun `should not match non XML payloads`() {
        assertFalse(deserializer.accept(relaxedMockk()))
    }

    @Test
    internal fun `should convert the XML payload`() {
        val jsonPayload = "<root><field>value</field></root>".toByteArray()
        val result = deserializer.convert(jsonPayload, relaxedMockk(), Entity::class)

        assertThat(result).isNotNull().isDataClassEqualTo(Entity("value"))
    }

    private data class Entity(val field: String)
}
