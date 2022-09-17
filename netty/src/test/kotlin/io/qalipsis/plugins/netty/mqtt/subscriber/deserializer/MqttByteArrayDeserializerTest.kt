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