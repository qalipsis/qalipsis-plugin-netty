package io.qalipsis.plugins.netty.mqtt.subscriber.deserializer

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/**
 * @author Gabriel Moraes
 */
internal class MqttByteArrayDeserializerTest{

    @Test
    fun `should return byte array`(){
        val deserializer = MqttByteArrayDeserializer()

        val result = deserializer.deserialize("Test message with accentuation: é".toByteArray())

        assertThat(result).isEqualTo("Test message with accentuation: é".toByteArray())
    }
}