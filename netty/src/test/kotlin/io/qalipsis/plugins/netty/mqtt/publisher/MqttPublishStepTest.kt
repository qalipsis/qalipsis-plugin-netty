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

import io.aerisconsulting.catadioptre.setProperty
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Meter
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.mqtt.MqttClient
import io.qalipsis.plugins.netty.mqtt.MqttClientOptions
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class MqttPublishStepTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private var recordsFactory: (suspend (ctx: StepContext<*, *>, input: Any) -> List<MqttPublishRecord>) =
        relaxedMockk { }

    @RelaxedMockK
    private lateinit var clientOptions: MqttClientOptions

    @RelaxedMockK
    private lateinit var workerGroupSupplier: EventLoopGroupSupplier

    private val eventsLogger = relaxedMockk<EventsLogger>()

    @Test
    fun `should publish without recording metrics`() = testDispatcherProvider.runTest {
        coEvery { recordsFactory.invoke(any(), any()) } returns listOf(MqttPublishRecord("payload", "test"))

        val mqttPublishStep =
            MqttPublishStep(StepName(), null, workerGroupSupplier, null, eventsLogger, clientOptions, recordsFactory)
        mqttPublishStep.setProperty("mqttClient", relaxedMockk<MqttClient> { })

        val context = StepTestHelper.createStepContext<Any, MqttPublishResult<Any>>(input = "Any")
        mqttPublishStep.execute(context)
    }

    @Test
    fun `should publish recording metrics`() = testDispatcherProvider.runTest {
        coEvery { recordsFactory.invoke(any(), any()) } returns listOf(MqttPublishRecord("payload", "test"))
        val recordsCountMock = relaxedMockk<Counter> { }
        val sentBytesMock = relaxedMockk<Counter> { }
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toEventTags() } returns emptyMap()
            every { scenarioName } returns "scenario-name"
            every { stepName } returns "step-name"
        }
        val tags: Map<String, String> = startStopContext.toEventTags()
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every {
                counter(
                    "scenario-name",
                    "step-name",
                    "netty-mqtt-publish-sent-records",
                    refEq(tags)
                )
            } returns recordsCountMock
            every { recordsCountMock.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>()) } returns recordsCountMock
            every {
                counter(
                    "scenario-name",
                    "step-name",
                    "netty-mqtt-publish-sent-value-bytes",
                    refEq(tags)
                )
            } returns sentBytesMock
            every { sentBytesMock.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>()) } returns sentBytesMock
        }
        val mqttPublishStep =
            MqttPublishStep(
                StepName(),
                null,
                workerGroupSupplier,
                meterRegistry,
                eventsLogger,
                clientOptions,
                recordsFactory
            )
        mqttPublishStep.setProperty("mqttClient", relaxedMockk<MqttClient> { })

        val context = StepTestHelper.createStepContext<Any, MqttPublishResult<Any>>(input = "Any")
        mqttPublishStep.start(startStopContext)
        mqttPublishStep.execute(context)

        verify {
            recordsCountMock.increment(1.0)
            recordsCountMock.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            sentBytesMock.increment(eq("payload".toByteArray().size.toDouble()))
            sentBytesMock.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
        }

        confirmVerified(recordsCountMock, sentBytesMock)
    }
}
