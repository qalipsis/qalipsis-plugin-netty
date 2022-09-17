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

package io.qalipsis.plugins.netty.mqtt.subscriber

import com.fasterxml.jackson.databind.ObjectMapper
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.messaging.deserializer.MessageDeserializer
import io.qalipsis.api.messaging.deserializer.MessageJsonDeserializer
import io.qalipsis.api.messaging.deserializer.MessageStringDeserializer
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.onEach
import io.qalipsis.plugins.netty.mqtt.spec.MqttVersion
import io.qalipsis.plugins.netty.mqtt.subscriber.spec.mqttSubscribe
import io.qalipsis.plugins.netty.netty
import java.beans.ConstructorProperties
import java.io.Serializable

internal object MqttSubscribeScenario {

    internal var portContainer = 0
    internal var hostContainer = "localhost"

    internal const val minions = 1

    internal val receivedMessages = concurrentSet<String>()

    @Scenario
    fun subscribeRecordsStringDeserializer() {
        scenario("subscriber-mqtt-string-deserializer") {
            minionsCount = minions
            profile {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }.start().netty().mqttSubscribe {
            clientName("test")
            concurrency(2)
            connect {
                host = hostContainer
                port = portContainer
            }
            protocol(MqttVersion.MQTT_3_1_1)
            topicFilter("test")
        }.deserialize(MessageStringDeserializer::class)
            .onEach { receivedMessages.add(it.value!!) }
    }

    @Scenario
    fun subscribeRecordsJsonDeserializer() {
        scenario("subscriber-mqtt-json-deserializer") {
            minionsCount = minions
            profile {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }.start().netty().mqttSubscribe {
            clientName("testjson")
            concurrency(2)
            connect {
                host = hostContainer
                port = portContainer
            }
            protocol(MqttVersion.MQTT_3_1_1)
            topicFilter("test/json")
        }.deserialize(MessageJsonDeserializer(User::class))
            .onEach { receivedMessages.add(it.value!!.id) }
    }

    @Scenario
    fun subscribeRecordsByteArrayDeserializer() {
        scenario("subscriber-mqtt-bytearray-deserializer") {
            minionsCount = minions
            profile {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }.start().netty().mqttSubscribe {
            clientName("testbytearray")
            concurrency(2)
            connect {
                host = hostContainer
                port = portContainer
            }
            protocol(MqttVersion.MQTT_3_1_1)
            topicFilter("test/bytearray")
        }.onEach {
            receivedMessages.add(it.value!!.decodeToString())
        }
    }

    @Scenario
    fun subscribeRecordsCustomDeserializer() {
        scenario("subscriber-mqtt-custom-deserializer") {
            minionsCount = minions
            profile {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }.start().netty().mqttSubscribe {
            clientName("testcustom")
            concurrency(2)
            connect {
                host = hostContainer
                port = portContainer
            }
            protocol(MqttVersion.MQTT_3_1_1)
            topicFilter("test/custom")
        }.deserialize(UserDeserializer::class)
            .onEach { receivedMessages.add(it.value!!.id) }
    }


    class UserDeserializer : MessageDeserializer<User>, Serializable {
        override fun deserialize(body: ByteArray): User {
            return ObjectMapper().readValue(body, User::class.java)
        }
    }

    data class User @ConstructorProperties("id") constructor(val id: String)
}