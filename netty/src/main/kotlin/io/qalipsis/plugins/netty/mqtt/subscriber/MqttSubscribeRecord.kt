package io.qalipsis.plugins.netty.mqtt.subscriber

import io.netty.handler.codec.mqtt.MqttProperties
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader

/**
 * Qalipsis representation of a consumed MQTT record.
 *
 * @author Gabriel Moraes
 *
 * @property offset of the record consumed by Qalipsis.
 * @property consumedTimestamp timestamp when the message was consumed by Qalipsis.
 * @property value of the record deserialized.
 * @property topicName name of the topic consumed.
 * @property packetId id of the packet send by the publisher.
 * @property properties MQTT properties of the message.
 */
data class MqttSubscribeRecord<V>(
        val offset: Long,
        val consumedTimestamp: Long,
        val value: V,
        val topicName: String,
        val packetId: Int,
        val properties: MqttProperties?
) {
    internal constructor(offset: Long, value: V, variableHeader: MqttPublishVariableHeader) : this(
            offset = offset,
            consumedTimestamp = System.currentTimeMillis(),
            value = value,
            topicName = variableHeader.topicName(),
            packetId = variableHeader.packetId(),
            properties = variableHeader.properties()
    )
}