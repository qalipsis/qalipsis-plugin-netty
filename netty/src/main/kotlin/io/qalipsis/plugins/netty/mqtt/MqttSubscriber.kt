package io.qalipsis.plugins.netty.mqtt

import io.netty.handler.codec.mqtt.MqttPublishMessage
import io.netty.handler.codec.mqtt.MqttQoS

/**
 * MQTT subscriber abstraction for subscribe and handle messages coming from MQTT topics.
 *
 * @param topic topic to subscribe on.
 * @param qoS QOS level, defaults to AT_LEAST_ONCE. For more information,
 * [see here](https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#subscribe).
 * @param subscribeMessageHandler handler of the messages coming from the topic subscribed.
 */
internal data class MqttSubscriber(
    val topic: String,
    val qoS: MqttQoS = MqttQoS.AT_LEAST_ONCE,
    val subscribeMessageHandler: (message: MqttPublishMessage) -> Unit
) {

    /**
     * Handles the incoming publish message from the MQTT broker and forwards the it to the subscriber.
     */
    fun handleMessage(mqttPublishMessage: MqttPublishMessage) {
        this.subscribeMessageHandler(mqttPublishMessage)
    }
}