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