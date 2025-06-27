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