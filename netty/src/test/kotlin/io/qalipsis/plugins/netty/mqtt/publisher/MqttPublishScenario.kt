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

package io.qalipsis.plugins.netty.mqtt.publisher

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.messaging.deserializer.MessageStringDeserializer
import io.qalipsis.api.scenario.scenario
import io.qalipsis.plugins.netty.mqtt.publisher.spec.mqttPublish
import io.qalipsis.plugins.netty.mqtt.spec.MqttQoS
import io.qalipsis.plugins.netty.mqtt.spec.MqttVersion
import io.qalipsis.plugins.netty.mqtt.subscriber.spec.mqttSubscribe
import io.qalipsis.plugins.netty.netty

internal object MqttPublishScenario {

    internal var portContainer = 0
    internal var hostContainer = "localhost"

    private const val minions = 1

    internal val receivedMessages = concurrentSet<String>()

    @Scenario("publisher-mqtt")
    fun publishRecords() {

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
            protocol(MqttVersion.MQTT_3_1)
            topicFilter("test")
            qoS(MqttQoS.EXACTLY_ONCE)
        }.deserialize(MessageStringDeserializer::class)
            .netty().mqttPublish {
                connect {
                    host = hostContainer
                    port = portContainer
                }
                protocol(MqttVersion.MQTT_3_1)
                clientName("publisher")
                records { _, input ->
                    listOf(
                        MqttPublishRecord(
                            input.value!!,
                            "publisher/topic",
                            qoS = MqttQoS.EXACTLY_ONCE,
                            retainedMessage = true
                        )
                    )
                }
            }
    }
}