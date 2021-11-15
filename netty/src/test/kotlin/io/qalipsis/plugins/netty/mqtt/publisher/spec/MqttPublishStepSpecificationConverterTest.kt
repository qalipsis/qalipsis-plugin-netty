package io.qalipsis.plugins.netty.mqtt.publisher.spec

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.mockk.spyk
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.mqtt.MqttClientOptions
import io.qalipsis.plugins.netty.mqtt.publisher.MqttPublishStep
import io.qalipsis.plugins.netty.mqtt.publisher.MqttPublishStepSpecificationConverter
import io.qalipsis.plugins.netty.mqtt.publisher.MqttPublisherMetrics
import io.qalipsis.plugins.netty.mqtt.spec.MqttVersion
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author Gabriel Moraes
 */
@WithMockk
@Suppress("UNCHECKED_CAST")
internal class MqttPublishStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<MqttPublishStepSpecificationConverter>() {

    @RelaxedMockK
    private lateinit var mockedClientOptions: MqttClientOptions

    @RelaxedMockK
    private lateinit var publisherMetrics: MqttPublisherMetrics

    @RelaxedMockK
    private lateinit var eventLoopGroupSupplier: EventLoopGroupSupplier

    @Test
    override fun `should not support unexpected spec`() {
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    override fun `should support expected spec`() {
        Assertions.assertTrue(converter.support(relaxedMockk<MqttPublishStepSpecificationImpl<*>>()))
    }

    @Test
    internal fun `should convert spec with name and retry policy`() = runBlockingTest {
        // given
        val spec = MqttPublishStepSpecificationImpl<Any>()
        spec.apply {
            name = "my-step"
            retryPolicy = mockedRetryPolicy
            connect {
                host = "localhost"
                port = 1889
            }
            records { _, _ ->
                listOf()
            }
            protocol(MqttVersion.MQTT_3_1)
            clientName("clientTest")
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        val spiedConverter = spyk(converter, recordPrivateCalls = true)

        every { spiedConverter["buildClientOptions"](refEq(spec.mqttPublishConfiguration)) } returns mockedClientOptions

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<MqttPublishStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(MqttPublishStep::class).all {
                prop("id").isEqualTo("my-step")
                prop("mqttClientOptions").isEqualTo(mockedClientOptions)
                prop("eventLoopGroupSupplier").isSameAs(eventLoopGroupSupplier)
                prop("retryPolicy").isNotNull()
                prop("recordsFactory").isNotNull()
            }
        }
    }


    @Test
    internal fun `should convert spec without name and retry policy`() = runBlockingTest {
        // given
        val spec = MqttPublishStepSpecificationImpl<Any>()
        spec.apply {
            connect {
                host = "localhost"
                port = 1889
            }
            records { _, _ ->
                listOf()
            }
            protocol(MqttVersion.MQTT_3_1)
            clientName("clientTest")
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter, recordPrivateCalls = true)

        val stepIdSlot = slot<String>()
        every { spiedConverter["buildClientOptions"](refEq(spec.mqttPublishConfiguration)) } returns mockedClientOptions
        every { spiedConverter["buildMetrics"](refEq(spec.mqttPublishConfiguration.metricsConfiguration), capture(stepIdSlot)) } returns publisherMetrics

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<MqttPublishStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(MqttPublishStep::class).all {
                prop("id").isNotNull().isEqualTo(stepIdSlot.captured)
                prop("mqttClientOptions").isEqualTo(mockedClientOptions)
                prop("eventLoopGroupSupplier").isSameAs(eventLoopGroupSupplier)
                prop("metrics").isEqualTo(publisherMetrics)
                prop("retryPolicy").isNull()
                prop("recordsFactory").isNotNull()
            }
        }
    }


}
