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

package io.qalipsis.plugins.netty.mqtt

import io.qalipsis.plugins.netty.mqtt.spec.MqttAuthentication
import io.qalipsis.plugins.netty.mqtt.spec.MqttConnectionConfiguration
import io.qalipsis.plugins.netty.mqtt.spec.MqttVersion

/**
 * Client options regarding connection to the MQTT broker.
 *
 * @param connectionConfiguration configuration for connect to the broker.
 * @param authentication parameters to authenticate the communication with the broker.
 * @param clientId client id used to identify the client.
 * @param protocolVersion MQTT protocol version.
 * @param keepAliveSeconds keep alive timeout for the connection in  seconds, defaults to 60.
 */
internal data class MqttClientOptions(
    val connectionConfiguration: MqttConnectionConfiguration,
    val authentication: MqttAuthentication,
    val clientId: String,
    val protocolVersion: MqttVersion,
    val keepAliveSeconds: Int = 60
)
