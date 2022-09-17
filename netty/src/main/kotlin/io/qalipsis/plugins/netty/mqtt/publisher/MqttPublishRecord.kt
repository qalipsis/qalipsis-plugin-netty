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