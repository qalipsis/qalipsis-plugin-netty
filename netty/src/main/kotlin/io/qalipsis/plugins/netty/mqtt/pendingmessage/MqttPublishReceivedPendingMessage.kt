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
