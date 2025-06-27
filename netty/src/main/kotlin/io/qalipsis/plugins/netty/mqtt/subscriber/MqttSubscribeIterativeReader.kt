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

package io.qalipsis.plugins.netty.mqtt.subscriber

import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.codec.mqtt.MqttPublishMessage
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceIterativeReader
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.mqtt.MqttClient
import io.qalipsis.plugins.netty.mqtt.MqttClientOptions
import io.qalipsis.plugins.netty.mqtt.MqttSubscriber
import io.qalipsis.plugins.netty.mqtt.spec.MqttQoS
import kotlinx.coroutines.channels.Channel

/**
 * Implementation of [DatasourceIterativeReader] to consumer records from MQTT topics.
 *
 * @property concurrency quantity of threads used in [NioEventLoopGroup].
 * @property mqttClientOptions configuration for MQTT connection.
 * @property topic name of the topic.
 * @property subscribeQoS quality of service for the subscriber.
 *
 * @author Gabriel Moraes
 */
internal class MqttSubscribeIterativeReader(
    private val mqttClientOptions: MqttClientOptions,
    private val concurrency: Int,
    private val topic: String,
    private val subscribeQoS: MqttQoS
) : DatasourceIterativeReader<MqttPublishMessage> {

    private var running = false

    private var resultChannel: Channel<MqttPublishMessage>? = null

    private var mqttClient: MqttClient? = null

    override fun start(context: StepStartStopContext) {
        log.debug { "Starting the MQTT consumer" }
        running = true
        resultChannel = Channel(Channel.UNLIMITED)

        mqttClient = MqttClient(mqttClientOptions, NativeTransportUtils.getEventLoopGroup(concurrency))

        startConsumer(mqttClient!!)
    }

    private fun startConsumer(mqttClient: MqttClient) {
        val subscription = MqttSubscriber(topic, subscribeQoS.mqttNativeQoS) { message ->
            resultChannel?.trySend(message)?.getOrThrow()
        }

        mqttClient.subscribe(subscription)
    }

    override fun stop(context: StepStartStopContext) {
        log.debug { "Stopping the MQTT consumer" }

        running = false
        mqttClient?.close()
        resultChannel = null
    }

    override suspend fun hasNext(): Boolean {
        return running
    }

    override suspend fun next(): MqttPublishMessage {
        return resultChannel!!.receive()
    }

    companion object {
        private val log = logger()
    }
}
