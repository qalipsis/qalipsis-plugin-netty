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