package io.qalipsis.plugins.netty.mqtt.publisher

import io.netty.handler.codec.mqtt.MqttProperties
import io.qalipsis.plugins.netty.mqtt.spec.MqttQoS
import javax.validation.constraints.NotBlank

/**
 * Qalipsis representation of a MQTT record to publish.
 *
 * @property value of the record deserialized.
 * @property topicName name of the topic consumed.
 * @property qoS quality of service of the publisher.
 * @property properties MQTT properties of the message.
 * @property retainedMessage MQTT retained message property, defaults to true.
 *
 * @author Gabriel Moraes
 */
data class MqttPublishRecord(
    @field:NotBlank val value: String,
    @field:NotBlank val topicName: String,
    val qoS: MqttQoS = MqttQoS.AT_LEAST_ONCE,
    val properties: MqttProperties? = null,
    val retainedMessage: Boolean = true
)