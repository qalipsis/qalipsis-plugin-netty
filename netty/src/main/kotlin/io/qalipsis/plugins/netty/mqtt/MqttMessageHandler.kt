package io.qalipsis.plugins.netty.mqtt

import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.mqtt.MqttConnAckMessage
import io.netty.handler.codec.mqtt.MqttConnectReturnCode
import io.netty.handler.codec.mqtt.MqttFixedHeader
import io.netty.handler.codec.mqtt.MqttMessage
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader
import io.netty.handler.codec.mqtt.MqttMessageType
import io.netty.handler.codec.mqtt.MqttPubAckMessage
import io.netty.handler.codec.mqtt.MqttPublishMessage
import io.netty.handler.codec.mqtt.MqttQoS
import io.netty.handler.codec.mqtt.MqttSubAckMessage
import io.netty.util.ReferenceCountUtil
import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.lang.tryAndLog
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.netty.mqtt.pendingmessage.MqttPendingMessage
import io.qalipsis.plugins.netty.mqtt.pendingmessage.MqttPublishReceivedPendingMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer

/**
 * MQTT message handler for sending and receiving messages from a MQTT broker.
 *
 * @author Gabriel Moraes
 */
internal class MqttMessageHandler : BiConsumer<ChannelHandlerContext, MqttMessage> {

    private var channel: Channel? = null
    private var subscriber: MqttSubscriber? = null
    private var pendingMessages: MutableList<MqttPendingMessage> = concurrentList()


    /**
     * Listens from MQTT messages and process them. For more info about the messages received,
     * see [here](https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#pubcomp).
     *
     * @param channelHandlerContext channel context related to the message.
     * @param mqttMessage message sent by the broker.
     */
    override fun accept(channelHandlerContext: ChannelHandlerContext, mqttMessage: MqttMessage) {
        log.debug { "Receiving message type: ${mqttMessage.fixedHeader().messageType()}" }
        when (mqttMessage.fixedHeader().messageType()) {
            MqttMessageType.CONNACK -> handleConnectionAck(
                channelHandlerContext.channel(),
                mqttMessage as MqttConnAckMessage
            )
            MqttMessageType.SUBACK -> handleSubscribeAck(mqttMessage as MqttSubAckMessage)
            MqttMessageType.PUBACK -> handlePublishAck(mqttMessage as MqttPubAckMessage)
            MqttMessageType.PUBLISH -> handlePublish(
                channelHandlerContext.channel(),
                ReferenceCountUtil.retain(mqttMessage as MqttPublishMessage)
            )
            MqttMessageType.PUBREC -> handlePublishReceived(mqttMessage)
            MqttMessageType.PUBREL -> handlePublishRelease(mqttMessage)
            MqttMessageType.PUBCOMP -> handlePublishCompleted(mqttMessage)
            MqttMessageType.DISCONNECT -> close()
            else -> {
                log.info { "MQTT message type ${mqttMessage.fixedHeader().messageType()} not supported" }
            }
        }
    }

    /**
     * Listens for the connection future and add a listener on the close channel event.
     *
     * @param channelFuture channel future for the connection.
     */
    fun connectionListener(channelFuture: ChannelFuture) {
        channelFuture.addListener { future ->
            if (future.isSuccess) {
                channelFuture.channel()?.closeFuture()?.addListener {
                    close()
                    log.info { "Closing connection to MQTT broker" }
                }
            }
        }
    }

    /**
     * Sends a subscribe message to the broker if connected, if not it will wail until receive an ack for the
     * connection and sends the [MqttPendingMessage] to the broker.
     *
     * @param mqttSubscriber subscription for a topic.
     * @param pendingMessageSubscription subscriber message that periodically sends it to the broker until it receives
     * an ack.
     */
    fun subscribe(mqttSubscriber: MqttSubscriber, pendingMessageSubscription: MqttPendingMessage) {
        this.subscriber = mqttSubscriber
        pendingMessages.add(pendingMessageSubscription)

        if (sendAndFlushPacket(pendingMessageSubscription.pendingMessage) != null) {
            pendingMessageSubscription.start(this.channel!!.eventLoop().next(), this::sendAndFlushPacket)
        }
    }

    /**
     * Sends a publish message to the broker if connected, if not it will wail until receive an ack for the
     * connection and sends the [MqttPendingMessage] to the broker.
     *
     * @param pendingPublishMessage publish message that periodically sends it to the broker until it receives an ack.
     */
    fun publish(pendingPublishMessage: MqttPendingMessage) {
        pendingMessages.add(pendingPublishMessage)
        if (sendAndFlushPacket(pendingPublishMessage.pendingMessage) != null) {
            pendingPublishMessage.start(this.channel!!.eventLoop().next(), this::sendAndFlushPacket)
        }
    }

    /**
     * Sends a message and flush the channel if the current channel is active, if it is not then does nothing.
     */
    private fun sendAndFlushPacket(message: MqttMessage): ChannelFuture? {
        return if (this.channel?.isActive == true) {
            log.debug { "Sending and flush channel with message type: ${message.fixedHeader().messageType()}" }

            this.channel?.writeAndFlush(ReferenceCountUtil.retain(message))
        } else this.channel?.newFailedFuture(IllegalStateException("Channel is closed!"))
    }

    /**
     * Handles subscribe ack message, find it and remove from the pending messages.
     */
    private fun handleSubscribeAck(mqttSubAckMessage: MqttSubAckMessage) {
        val messageId = mqttSubAckMessage.idAndPropertiesVariableHeader().messageId()
        findAndRemovePendingMessage(messageId)
    }

    /**
     * Handles publish ack message, find it and remove from the pending messages.
     */
    private fun handlePublishAck(mqttPubAckMessage: MqttPubAckMessage) {
        val messageId = mqttPubAckMessage.variableHeader().messageId()
        findAndRemovePendingMessage(messageId)
    }

    /**
     * Handles connection ack message.
     * When connection is accepted, sends all the pending messages.
     * When connection is not accepted, closes the channel.
     */
    private fun handleConnectionAck(channel: Channel, message: MqttConnAckMessage) {
        if (message.variableHeader().connectReturnCode() != MqttConnectReturnCode.CONNECTION_ACCEPTED) {
            channel.close()
            this.channel?.close()

        } else {
            this.channel = channel
            sendPendingMessages(channel)
        }
    }

    private fun sendPendingMessages(channel: Channel) {
        pendingMessages.forEach {
            sendAndFlushPacket(it.pendingMessage)
            it.start(channel.eventLoop().next(), this::sendAndFlushPacket)
        }
    }

    /**
     * Handles publishes message coming from the broker and the QOS involved.
     * For more information see [here](https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#publish)
     */
    private fun handlePublish(channel: Channel, message: MqttPublishMessage) {
        when (message.fixedHeader().qosLevel()) {
            MqttQoS.AT_MOST_ONCE -> subscriber?.handleMessage(message)
            MqttQoS.AT_LEAST_ONCE -> {
                sendPubAckMessage(message)
            }
            MqttQoS.EXACTLY_ONCE -> {
                if (message.variableHeader().packetId() != -1) {
                    sendPublishReceivedMessage(message, channel)
                }
            }
            else -> log.warn { "Not supported Qos Level" }
        }
    }

    /**
     * Sends publish ack message when publish message has QOS of 1 and delegates message to subscriber.
     */
    private fun sendPubAckMessage(message: MqttPublishMessage) {
        subscriber?.handleMessage(message)

        val pubAckMessage = buildMessageByTypeAndId(MqttMessageType.PUBACK, message.variableHeader().packetId())
        sendAndFlushPacket(pubAckMessage)
    }

    /**
     * Sends publish received message when publish message has QOS of 2.
     * It does not delegate the message to subscriber yet, as in the QOS level 2 it is done when the PUBREL message
     * is handled.
     */
    private fun sendPublishReceivedMessage(message: MqttPublishMessage, channel: Channel) {
        val messageId = message.variableHeader().packetId()
        val publishReceivedMessage = buildMessageByTypeAndId(MqttMessageType.PUBREC, messageId)
        sendAndFlushPacket(publishReceivedMessage)

        val pendingQo2PendingPublish = MqttPublishReceivedPendingMessage(message, publishReceivedMessage, messageId)
        pendingMessages.add(pendingQo2PendingPublish)
        pendingQo2PendingPublish.start(channel.eventLoop().next(), this::sendAndFlushPacket)
    }

    /**
     * Handles PUBREL message coming from the broker.
     * Delegates the original publish message to the subscriber.
     * Finds and remove the pending message related to the [MqttPublishReceivedPendingMessage].
     */
    private fun handlePublishRelease(message: MqttMessage) {

        val messageId = (message.variableHeader() as MqttMessageIdVariableHeader).messageId()
        findAndRemovePendingMessage(messageId) {
            subscriber?.handleMessage((it as MqttPublishReceivedPendingMessage).retainedPublishMessage)
        }

        sendPublishComplete(messageId)
    }


    /**
     * Handles PUBCOMP message coming from the broker.
     * Finds and remove the pending PUBREL message.
     * This is the last step of a publish using qoS 2.
     */
    private fun handlePublishCompleted(message: MqttMessage) {

        val messageId = (message.variableHeader() as MqttMessageIdVariableHeader).messageId()
        findAndRemovePendingMessage(messageId)
    }


    /**
     * Sends the publish complete message as the last step in the publish message with QOS level 2.
     */
    private fun sendPublishComplete(messageId: Int) {
        val publishCompleteMessage = buildMessageByTypeAndId(MqttMessageType.PUBCOMP, messageId)

        sendAndFlushPacket(publishCompleteMessage)
    }

    /**
     * Handles PUBREC message coming from the broker.
     */
    private fun handlePublishReceived(message: MqttMessage) {

        val messageId = (message.variableHeader() as MqttMessageIdVariableHeader).messageId()
        findAndRemovePendingMessage(messageId)

        sendPublishReleaseMessage(messageId)
    }

    /**
     * Sends the publish release message to the broker.
     */
    private fun sendPublishReleaseMessage(messageId: Int) {
        val publishReleaseMessage = buildMessageByTypeAndId(MqttMessageType.PUBREL, messageId)
        sendAndFlushPacket(publishReleaseMessage)
        addPendingMessage(publishReleaseMessage, messageId)
    }

    /**
     * Builds MQTT messages by [MqttMessageType] and [messageId].
     */
    private fun buildMessageByTypeAndId(type: MqttMessageType, messageId: Int): MqttMessage {
        val fixedHeader = MqttFixedHeader(type, false, MqttQoS.AT_MOST_ONCE, false, 0)
        val variableHeader = MqttMessageIdVariableHeader.from(messageId)

        return MqttMessage(fixedHeader, variableHeader)
    }

    /**
     * Finds and removes pending messages.
     */
    private fun findAndRemovePendingMessage(messageId: Int, handler: ((MqttPendingMessage) -> Unit)? = null) {
        val itemIndex = pendingMessages.indexOfFirst { it.packetId == messageId }
        if (itemIndex >= 0) {
            val pendingQos2Message = pendingMessages.removeAt(itemIndex)
            pendingQos2Message.onResponse()
            handler?.invoke(pendingQos2Message)
        }
    }

    /**
     * Adds pending messages and start to republish them.
     */
    private fun addPendingMessage(mqttMessage: MqttMessage, messageId: Int) {
        val pendingMessage = MqttPendingMessage(mqttMessage, messageId)
        pendingMessages.add(pendingMessage)
        pendingMessage.start(channel!!.eventLoop().next(), this::sendAndFlushPacket)
    }

    /**
     * Closes the current channel.
     */
    fun close() {

        val latch = CountDownLatch(pendingMessages.size)

        latch.await(CLOSE_TIMEOUT, TimeUnit.SECONDS)
        tryAndLog(log) { channel?.close() }
    }

    companion object {

        @JvmStatic
        private val log = logger()

        /**
         * Default timeout used to await for pending messages to be sent before closing the channel.
         */
        private const val CLOSE_TIMEOUT = 5L
    }
}
