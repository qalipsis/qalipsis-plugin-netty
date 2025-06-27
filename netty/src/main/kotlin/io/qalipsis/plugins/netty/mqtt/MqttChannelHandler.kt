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

package io.qalipsis.plugins.netty.mqtt;

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.mqtt.MqttFixedHeader
import io.netty.handler.codec.mqtt.MqttMessage
import io.netty.handler.codec.mqtt.MqttMessageBuilders
import io.netty.handler.codec.mqtt.MqttMessageType
import io.netty.handler.codec.mqtt.MqttMessageType.CONNECT
import io.netty.handler.codec.mqtt.MqttProperties
import io.netty.handler.codec.mqtt.MqttQoS.AT_LEAST_ONCE
import io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE
import io.netty.handler.timeout.IdleStateEvent
import java.util.function.BiConsumer

/**
 * Implementation of [SimpleChannelInboundHandler] that reads from the netty channels.
 *
 * @param messageHandler handler for channel read messages.
 * @param mqttClientOptions client options when connection to MQTT broker.
 *
 * @author Gabriel Moraes
 */
@ChannelHandler.Sharable
internal class MqttChannelHandler(
    private var messageHandler: BiConsumer<ChannelHandlerContext, MqttMessage>,
    private val mqttClientOptions: MqttClientOptions,
) : SimpleChannelInboundHandler<MqttMessage>() {

    /**
     * Delegates the channel incoming messages to the [MqttMessageHandler].
     */
    @Throws(Exception::class)
    override fun channelRead0(ctx: ChannelHandlerContext, msg: MqttMessage) {
        messageHandler.accept(ctx, msg)
    }

    /**
     * Sends the [CONNECT] message to the broker.
     */
    @Throws(Exception::class)
    override fun channelActive(ctx: ChannelHandlerContext) {

        super.channelActive(ctx)
        val version = mqttClientOptions.protocolVersion
        val authentication = mqttClientOptions.authentication

        val connectMessageBuilder = MqttMessageBuilders.connect().clientId(mqttClientOptions.clientId)
            .hasPassword(authentication.password.isNotBlank())
            .hasUser(authentication.username.isNotBlank())
            .keepAlive(mqttClientOptions.keepAliveSeconds)
            .properties(MqttProperties.NO_PROPERTIES)
            .cleanSession(false)
            .willFlag(false)
            .willRetain(false)
            .willQoS(AT_MOST_ONCE)
            .protocolVersion(version.mqttNativeVersion)

        if (authentication.password.isNotBlank()) {
            connectMessageBuilder
                .password(mqttClientOptions.authentication.password.toByteArray())
                .username(mqttClientOptions.authentication.username)
        }

        ctx.channel().writeAndFlush(connectMessageBuilder.build())
    }

    /**
     * Receives the channel active event.
     */
    @Throws(Exception::class)
    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
    }

    /**
     * Sends a ping request to the broker when in [IdleStateEvent].
     */
    @Throws(Exception::class)
    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {

        if (evt is IdleStateEvent) {
            val pingReqFixedHeader = MqttFixedHeader(MqttMessageType.PINGREQ, false, AT_LEAST_ONCE, false, 0)
            ctx.writeAndFlush(MqttMessage(pingReqFixedHeader))
        } else {
            super.userEventTriggered(ctx, evt)
        }
    }

    /**
     * Caught exceptions from the channel and closes it.
     */
    @Throws(Exception::class)
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        cause.printStackTrace()
        ctx.close()
    }

}