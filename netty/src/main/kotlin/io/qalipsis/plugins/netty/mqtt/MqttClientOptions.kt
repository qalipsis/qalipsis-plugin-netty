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
