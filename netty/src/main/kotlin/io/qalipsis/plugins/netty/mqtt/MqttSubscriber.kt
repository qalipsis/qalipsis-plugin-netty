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