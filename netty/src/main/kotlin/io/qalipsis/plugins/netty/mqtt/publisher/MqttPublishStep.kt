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

package io.qalipsis.plugins.netty.mqtt.publisher

import io.netty.channel.EventLoopGroup
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.mqtt.MqttClient
import io.qalipsis.plugins.netty.mqtt.MqttClientOptions


/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to publish a message into MQTT broker.
 *
 * @property id id of the step.
 * @property retryPolicy of the step.
 * @property mqttClientOptions client options to connect to the MQTT broker.
 * @property recordsFactory closure to generate the records to publish.
 * @property eventsLogger the logger for events.
 * @property meterRegistry registry for the meters.
 * @author Gabriel Moraes
 */
internal class MqttPublishStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val eventLoopGroupSupplier: EventLoopGroupSupplier,
    private val meterRegistry: CampaignMeterRegistry?,
    private val eventsLogger: EventsLogger?,
    private val mqttClientOptions: MqttClientOptions,
    private val recordsFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<MqttPublishRecord>),
) : AbstractStep<I, MqttPublishResult<I>>(id, retryPolicy) {

    private lateinit var mqttClient: MqttClient

    private lateinit var eventLoopGroup: EventLoopGroup

    private val meterPrefix = "netty-mqtt-publish"

    private val eventPrefix = "netty.mqtt.publish"

    private var recordsCounter: Counter? = null

    private var valueBytesCounter: Counter? = null

    override suspend fun start(context: StepStartStopContext) {
        val tags = context.toEventTags()
        val scenarioName = context.scenarioName
        val stepName = context.stepName
        meterRegistry?.apply {
            recordsCounter = counter(scenarioName, stepName, "$meterPrefix-sent-records", tags).report {
                display(
                    format = "rec attempted: %,.0f",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 0,
                ) { count() }
            }
            valueBytesCounter = counter(scenarioName, stepName, "$meterPrefix-sent-value-bytes", tags).report {
                display(
                    format = "rec sent: %,.0f bytes",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 1,
                ) { count() }
            }
        }
        eventLoopGroup = eventLoopGroupSupplier.getGroup()
        mqttClient = MqttClient(mqttClientOptions, eventLoopGroup)
    }

    override suspend fun execute(context: StepContext<I, MqttPublishResult<I>>) {
        val tags = context.toEventTags()
        val input = context.receive()

        val recordsToSend = recordsFactory(context, input)
        val recordsCount = recordsToSend.size
        var valueBytesCount = 0

        eventsLogger?.debug("${eventPrefix}.sending-records", recordsToSend.size, tags = tags)
        recordsCounter?.increment(recordsToSend.size.toDouble())
        recordsToSend.forEach {
            val payload = it.value.toByteArray()
            valueBytesCount += payload.size
            mqttClient.publish(it.topicName, payload, it.retainedMessage, it.qoS.mqttNativeQoS)
        }
        eventsLogger?.info("${eventPrefix}.sending.value-bytes", valueBytesCount, tags = tags)
        valueBytesCounter?.increment(valueBytesCount.toDouble())
        val mqttPublishMeters = MqttPublishMeters(
            recordsCount = recordsCount,
            sentBytes = valueBytesCount
        )

        context.send(
            MqttPublishResult(
                input,
                mqttPublishMeters,
                recordsToSend
            )
        )
    }

    override suspend fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            recordsCounter = null
            valueBytesCounter = null
        }
        mqttClient.close()
        eventLoopGroup.shutdownGracefully()
    }
}
