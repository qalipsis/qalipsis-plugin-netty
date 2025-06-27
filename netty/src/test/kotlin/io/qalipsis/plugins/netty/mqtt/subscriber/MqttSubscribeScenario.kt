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

    @Scenario("subscriber-mqtt-string-deserializer")
    fun subscribeRecordsStringDeserializer() {
        scenario {
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

    @Scenario("subscriber-mqtt-json-deserializer")
    fun subscribeRecordsJsonDeserializer() {
        scenario {
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

    @Scenario("subscriber-mqtt-bytearray-deserializer")
    fun subscribeRecordsByteArrayDeserializer() {
        scenario {
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

    @Scenario("subscriber-mqtt-custom-deserializer")
    fun subscribeRecordsCustomDeserializer() {
        scenario {
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