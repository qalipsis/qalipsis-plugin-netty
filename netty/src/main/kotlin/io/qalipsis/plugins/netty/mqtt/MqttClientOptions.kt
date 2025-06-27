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
