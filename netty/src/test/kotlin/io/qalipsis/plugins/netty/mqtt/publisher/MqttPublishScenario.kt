package io.qalipsis.plugins.netty.mqtt.publisher

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.messaging.deserializer.MessageStringDeserializer
import io.qalipsis.api.rampup.regular
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

    @Scenario
    fun publishRecords() {

        scenario("publisher-mqtt") {
            minionsCount = minions
            rampUp {
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