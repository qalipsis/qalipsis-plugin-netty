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
