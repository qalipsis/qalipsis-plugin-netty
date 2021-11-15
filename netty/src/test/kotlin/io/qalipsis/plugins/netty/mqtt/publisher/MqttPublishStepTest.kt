package io.qalipsis.plugins.netty.mqtt.publisher

import io.aerisconsulting.catadioptre.setProperty
import io.micrometer.core.instrument.Counter
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.mockk.verify
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
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

    @Test
    fun `should publish without recording metrics`() = runBlockingTest {
        coEvery { recordsFactory.invoke(any(), any()) } returns listOf(MqttPublishRecord("payload", "test"))
        val publisherMetrics = spyk(MqttPublisherMetrics())

        val mqttPublishStep =
            MqttPublishStep(StepId(), null, workerGroupSupplier, publisherMetrics, clientOptions, recordsFactory)
        mqttPublishStep.setProperty("mqttClient", relaxedMockk<MqttClient> { })

        val context = StepTestHelper.createStepContext<Any, Any>(input = "Any")
        mqttPublishStep.execute(context)

        verify(inverse = true) {
            publisherMetrics.recordsCount?.increment()
            publisherMetrics.sentBytes?.increment(any())
        }
    }

    @Test
    fun `should publish recording metrics`() = runBlockingTest {
        coEvery { recordsFactory.invoke(any(), any()) } returns listOf(MqttPublishRecord("payload", "test"))
        val recordsCountMock = relaxedMockk<Counter> { }
        val sentBytesMock = relaxedMockk<Counter> { }
        val publisherMetrics = MqttPublisherMetrics(recordsCountMock, sentBytesMock)

        val mqttPublishStep =
            MqttPublishStep(StepId(), null, workerGroupSupplier, publisherMetrics, clientOptions, recordsFactory)
        mqttPublishStep.setProperty("mqttClient", relaxedMockk<MqttClient> { })

        val context = StepTestHelper.createStepContext<Any, Any>(input = "Any")
        mqttPublishStep.execute(context)

        verify {
            recordsCountMock.increment()
            sentBytesMock.increment(eq("payload".toByteArray().size.toDouble()))
        }

        confirmVerified(recordsCountMock, sentBytesMock)
    }
}
