/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
