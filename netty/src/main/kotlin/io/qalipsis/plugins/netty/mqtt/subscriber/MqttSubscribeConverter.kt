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

import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.mqtt.MqttPublishMessage
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.messaging.deserializer.MessageDeserializer
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [DatasourceObjectConverter], that reads a message of native MQTT records and forwards each of
 * them converted as [MqttSubscribeRecord].
 *
 * @author Gabriel Moraes
 */
internal class MqttSubscribeConverter<V>(
    private val valueDeserializer: MessageDeserializer<V>,
    private val meterRegistry: CampaignMeterRegistry?,
    private val eventsLogger: EventsLogger?
) : DatasourceObjectConverter<MqttPublishMessage, MqttSubscribeRecord<V>> {

    private val meterPrefix = "netty-mqtt-subscribe-consumed"

    private val eventPrefix = "netty.mqtt.subscribe.consumed"

    private var recordsCounter: Counter? = null

    private lateinit var context: StepStartStopContext

    private var valueBytesCounter: Counter? = null

    override fun start(context: StepStartStopContext) {
        val tags = context.toEventTags()
        val scenarioName = context.scenarioName
        val stepName = context.stepName
        meterRegistry?.apply {
            recordsCounter = counter(scenarioName, stepName, "$meterPrefix-records", tags).report {
                display(
                    format = "received rec: %,.0f",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 0,
                    Counter::count
                )
            }
            valueBytesCounter = counter(scenarioName, stepName, "$meterPrefix-value-bytes", tags).report {
                display(
                    format = "received: %,.0f bytes",
                    severity = ReportMessageSeverity.INFO,
                    row = 1,
                    column = 1,
                    Counter::count
                )
            }
        }
        this.context = context
    }
    override fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            recordsCounter = null
            valueBytesCounter = null
        }
    }

    override suspend fun supply(
        offset: AtomicLong, value: MqttPublishMessage,
        output: StepOutput<MqttSubscribeRecord<V>>
    ) {
        val payload = ByteBufUtil.getBytes(value.payload())
        eventsLogger?.info("$eventPrefix.value-bytes", payload.size, tags = context.toEventTags())

        valueBytesCounter?.increment(payload.size.toDouble())
        recordsCounter?.increment()

        tryAndLogOrNull(log) {
            output.send(
                MqttSubscribeRecord(
                    offset.getAndIncrement(),
                    valueDeserializer.deserialize(payload),
                    value.variableHeader()
                )
            )
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
