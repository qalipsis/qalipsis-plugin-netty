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