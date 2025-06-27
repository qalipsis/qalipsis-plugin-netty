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

package io.qalipsis.plugins.netty.mqtt

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.mqtt.MqttDecoder
import io.netty.handler.codec.mqtt.MqttEncoder
import io.netty.handler.codec.mqtt.MqttMessageBuilders
import io.netty.handler.codec.mqtt.MqttQoS
import io.netty.handler.timeout.IdleStateHandler
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.PipelineHandlerNames
import io.qalipsis.plugins.netty.mqtt.pendingmessage.MqttPendingMessage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * MQTT client to create the connection and be able to push and subscribe to topics.
 *
 * @param clientOptions client options when connection to MQTT broker.
 * @param eventLoopGroup Netty event loop group used for handling the communication.
 *
 * @author Gabriel Moraes
 */
internal class MqttClient(
    clientOptions: MqttClientOptions, private val eventLoopGroup: EventLoopGroup
) {

    private val nextMessageId = AtomicInteger(1)

    private val bootstrap: Bootstrap

    private var reconnect: Boolean = true

    private val messageHandler = MqttMessageHandler()

    private val mqttHandler: MqttChannelHandler = MqttChannelHandler(
        mqttClientOptions = clientOptions,
        messageHandler = messageHandler
    )

    init {
        log.debug { "Trying to connect to MQTT broker" }

        this.reconnect = clientOptions.connectionConfiguration.reconnect
        bootstrap = Bootstrap()
            .remoteAddress(clientOptions.connectionConfiguration.host, clientOptions.connectionConfiguration.port)
            .channel(NativeTransportUtils.socketChannelClass)
            .option(ChannelOption.SO_REUSEADDR, true)
            .handler(MqttChannelInitializer(mqttHandler))
            .group(eventLoopGroup)


        connect(bootstrap)
    }

    private fun connect(bootstrap: Bootstrap) {
        val connection = bootstrap.connect()

        messageHandler.connectionListener(connection)
        scheduleReconnect(connection)
    }

    /**
     * Reconnects when the connections closes.
     */
    private fun scheduleReconnect(channelFuture: ChannelFuture) {

        channelFuture.addListener { future ->
            if (future.isSuccess) {
                channelFuture.channel()?.closeFuture()?.addListener {
                    reconnect()
                }
            } else {
                reconnect()
            }
        }
    }

    /**
     * Schedules a reconnection request.
     */
    private fun reconnect() {
        if (this.reconnect) {
            eventLoopGroup.schedule({
                connect(bootstrap)
            }, 500, TimeUnit.MILLISECONDS)
        }
    }


    /**
     * Gets and increments the message ID.
     */
    private fun getNewMessageId(): Int {
        return this.nextMessageId.getAndIncrement()
    }

    /**
     * Creates the subscription and delegates to the [MqttMessageHandler] to send it to the broker.
     */
    fun subscribe(mqttSubscriber: MqttSubscriber) {
        val messageId = getNewMessageId()
        val subscribeMessage = MqttMessageBuilders.subscribe()
            .messageId(messageId)
            .addSubscription(mqttSubscriber.qoS, mqttSubscriber.topic)
            .build()

        messageHandler.subscribe(mqttSubscriber, MqttPendingMessage(subscribeMessage, messageId))
    }


    fun publish(topicName: String, payload: ByteArray, qoS: MqttQoS = MqttQoS.AT_LEAST_ONCE) {
        publish(topicName, payload, true, qoS)
    }

    /**
     * Creates the publish message and delegates to the [MqttMessageHandler] to send it to the broker.
     */
    fun publish(topicName: String, payload: ByteArray, retained: Boolean = true, qoS: MqttQoS = MqttQoS.AT_LEAST_ONCE) {
        val byteBufPayload = Unpooled.buffer().writeBytes(payload)

        val messageId = getNewMessageId()
        val publishMessage = MqttMessageBuilders.publish()
            .messageId(messageId)
            .topicName(topicName)
            .payload(byteBufPayload)
            .retained(retained)
            .qos(qoS)
            .build()

        messageHandler.publish(MqttPendingMessage(publishMessage, messageId))
    }

    /**
     * Closes the netty event loop.
     */
    fun close() {
        log.debug { "Closing MQTT client" }
        messageHandler.close()
        reconnect = false
    }

    companion object {

        @JvmStatic
        private val log = logger()

        private const val DEFAULT_EVENT_LOOP_SHUTDOWN_TIMEOUT = 10000L
    }
}

/**
 * Channel initializer for MQTT netty client.
 *
 * @author
 */
internal class MqttChannelInitializer(
    private val mqttChannelHandler: MqttChannelHandler
) : ChannelInitializer<SocketChannel>() {

    override fun initChannel(channel: SocketChannel) {
        channel.pipeline().addLast(PipelineHandlerNames.REQUEST_ENCODER, MqttEncoder.INSTANCE)
        channel.pipeline().addLast(PipelineHandlerNames.REQUEST_DECODER, MqttDecoder())
        channel.pipeline().addLast(PipelineHandlerNames.REQUEST_HEARTBEAT_HANDLER, IdleStateHandler(5, 5, 0))
        channel.pipeline().addLast(PipelineHandlerNames.REQUEST_HANDLER, mqttChannelHandler)
    }
}
