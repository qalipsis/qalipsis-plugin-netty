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
