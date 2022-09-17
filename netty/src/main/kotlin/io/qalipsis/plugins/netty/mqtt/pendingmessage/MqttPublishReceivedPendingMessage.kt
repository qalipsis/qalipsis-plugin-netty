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

package io.qalipsis.plugins.netty.mqtt.pendingmessage

import io.netty.handler.codec.mqtt.MqttMessage
import io.netty.handler.codec.mqtt.MqttMessageType.PUBREC
import io.netty.handler.codec.mqtt.MqttPublishMessage

/**
 * Implementation of pending message for [PUBREC] type.
 *
 * @property retainedPublishMessage Publish message that was received but not consumed until [PUBREC] message
 * receives an ack.
 * @property message [PUBREC] message pending to receive an ack from the broker.
 *
 * @author Gabriel Moraes
 */
internal class MqttPublishReceivedPendingMessage(
    val retainedPublishMessage: MqttPublishMessage,
    val message: MqttMessage, packetId: Int
) : MqttPendingMessage(message, packetId)
