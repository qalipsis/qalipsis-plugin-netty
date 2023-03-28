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

package io.qalipsis.plugins.netty.mqtt.publisher

import io.aerisconsulting.catadioptre.setProperty
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Tags
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
        val metersTags = relaxedMockk<Tags>()
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every { counter("netty-mqtt-publish-sent-records", refEq(metersTags)) } returns recordsCountMock
            every { counter("netty-mqtt-publish-sent-value-bytes", refEq(metersTags)) } returns sentBytesMock
        }

        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns metersTags
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
            sentBytesMock.increment(eq("payload".toByteArray().size.toDouble()))
        }

        confirmVerified(recordsCountMock, sentBytesMock)
    }
}
