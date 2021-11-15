package io.qalipsis.plugins.netty.mqtt.spec

import javax.validation.constraints.Max
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive

/**
 * Connection configuration for a MQTT broker.
 *
 * @property host of the broker, defaults to "localhost".
 * @property port of the connection, defaults to "1883".
 * @property reconnect property to control whether the client should try to reconnect when the connection is closed.
 *
 * @author Gabriel Moraes
 */
data class MqttConnectionConfiguration(
    @field:NotBlank var host: String = "localhost",
    @field:Positive @field:Max(65535) var port: Int = 1883,
    var reconnect: Boolean = true
)

/**
 * Authentication configuration for a MQTT client.
 *
 * @property username user used for authenticating.
 * @property password password used for authenticating.
 *
 * @author Gabriel Moraes
 */
data class MqttAuthentication(
    var username: String = "",
    var password: String = ""
)


/**
 * MQTT protocol version used to define client communication with the broker.
 *
 * @author Gabriel Moraes
 */
enum class MqttVersion(internal val mqttNativeVersion: io.netty.handler.codec.mqtt.MqttVersion) {
    MQTT_3_1(io.netty.handler.codec.mqtt.MqttVersion.MQTT_3_1),
    MQTT_3_1_1(io.netty.handler.codec.mqtt.MqttVersion.MQTT_3_1_1),
    MQTT_5(io.netty.handler.codec.mqtt.MqttVersion.MQTT_5)
}

/**
 * MQTT subscriber Quality of Service used to subscribe to a topic.
 * For more information, see [here](https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#qos-flows)
 *
 * @author Gabriel Moraes
 */
enum class MqttQoS(internal val mqttNativeQoS: io.netty.handler.codec.mqtt.MqttQoS) {
    EXACTLY_ONCE(io.netty.handler.codec.mqtt.MqttQoS.EXACTLY_ONCE),
    AT_LEAST_ONCE(io.netty.handler.codec.mqtt.MqttQoS.AT_LEAST_ONCE),
    AT_MOST_ONCE(io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE)
}


