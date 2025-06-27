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
import io.netty.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/**
 * Retransmit the pending [MqttMessage] until they received an ack and stops the retransmission.
 *
 * @author Gabriel Moraes
 */
internal class RetransmissionHandler<T : MqttMessage>(
    private val pendingMessage: T
) {

    /**
     * Schedules a publisher call when the [secondsToTimeout] is reached.
     */
    private var timer: ScheduledFuture<*>? = null

    /**
     * Timeout in seconds used to republish the pending message to the broker.
     */
    private var secondsToTimeout = 5

    /**
     * Starts to retransmit the messages when the timeout is reached.
     */
    fun start(eventLoop: EventLoop, publisher: Consumer<MqttMessage>, timeout: Int = 5) {

        this.secondsToTimeout = timeout
        startTimer(eventLoop, publisher)
    }

    /**
     * Schedules a publish when the timeout is reached in a recursive way until it is cancelled.
     */
    private fun startTimer(eventLoop: EventLoop, publisher: Consumer<MqttMessage>) {
        timer = eventLoop.schedule({
            secondsToTimeout += 2
            publisher.accept(pendingMessage)
            startTimer(eventLoop, publisher)
        }, secondsToTimeout.toLong(), TimeUnit.SECONDS)
    }

    /**
     * Stops the retransmission and cancels the current scheduler.
     */
    fun stop() {
        timer?.cancel(true)
    }
}
