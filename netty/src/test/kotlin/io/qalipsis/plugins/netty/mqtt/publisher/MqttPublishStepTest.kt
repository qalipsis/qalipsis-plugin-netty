package io.qalipsis.plugins.netty.mqtt.publisher

import io.aerisconsulting.catadioptre.setProperty
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import io.mockk.every
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.mqtt.MqttClient
import io.qalipsis.plugins.netty.mqtt.MqttClientOptions
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test

@WithMockk
internal class MqttPublishStepTest {

    private var recordsFactory: (suspend (ctx: StepContext<*, *>, input: Any) -> List<MqttPublishRecord>) =
        relaxedMockk { }

    @RelaxedMockK
    private lateinit var clientOptions: MqttClientOptions

    @RelaxedMockK
    private lateinit var workerGroupSupplier: EventLoopGroupSupplier

    private val eventsLogger = relaxedMockk<EventsLogger>()

    @Test
    fun `should publish without recording metrics`() = runBlockingTest {
        coEvery { recordsFactory.invoke(any(), any()) } returns listOf(MqttPublishRecord("payload", "test"))

        val mqttPublishStep =
            MqttPublishStep(StepId(), null, workerGroupSupplier, null, eventsLogger, clientOptions, recordsFactory)
        mqttPublishStep.setProperty("mqttClient", relaxedMockk<MqttClient> { })

        val context = StepTestHelper.createStepContext<Any, MqttPublishResult<Any>>(input = "Any")
        mqttPublishStep.execute(context)
    }

    @Test
    fun `should publish recording metrics`() = runBlockingTest {
        coEvery { recordsFactory.invoke(any(), any()) } returns listOf(MqttPublishRecord("payload", "test"))
        val recordsCountMock = relaxedMockk<Counter> { }
        val sentBytesMock = relaxedMockk<Counter> { }
        val metersTags = relaxedMockk<Tags>()
        val meterRegistry = relaxedMockk<MeterRegistry> {
            every { counter("mqtt-publish-sent-records", refEq(metersTags)) } returns recordsCountMock
            every { counter("mqtt-publish-sent-value-bytes", refEq(metersTags)) } returns sentBytesMock
        }

        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns metersTags
        }
        val mqttPublishStep =
            MqttPublishStep(StepId(), null, workerGroupSupplier, meterRegistry, eventsLogger, clientOptions, recordsFactory)
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
