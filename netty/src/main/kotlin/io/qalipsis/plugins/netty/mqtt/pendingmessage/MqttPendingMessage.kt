package io.qalipsis.plugins.netty.mqtt.pendingmessage

import io.netty.channel.EventLoop
import io.netty.handler.codec.mqtt.MqttMessage
import java.util.function.Consumer

/**
 * Abstract class for publishing pending messages to the broker, making use of the [RetransmissionHandler].
 *
 * @param pendingMessage MQTT message that is being sent in the retransmission handler until it receives an ack.
 * @param packetId MQTT message packet id.
 *
 * @author Gabrel Moraes
 */
internal open class MqttPendingMessage(val pendingMessage: MqttMessage, val packetId: Int) {
    private val retransmissionHandler: RetransmissionHandler<MqttMessage> = RetransmissionHandler(pendingMessage)

    /**
     * Starts to retransmit the message.
     */
    fun start(eventLoop: EventLoop, sendPacket: Consumer<MqttMessage>) {
        retransmissionHandler.start(eventLoop, sendPacket)
    }

    /**
     * Calls the [RetransmissionHandler] to stop.
     */
    fun onResponse() {
        retransmissionHandler.stop()
    }
}