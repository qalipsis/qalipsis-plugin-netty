package io.qalipsis.plugins.netty.mqtt.subscriber.spec

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import io.aerisconsulting.catadioptre.invokeInvisible
import io.micrometer.core.instrument.Counter
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.mockk.spyk
import io.netty.handler.codec.mqtt.MqttPublishMessage
import io.qalipsis.api.messaging.deserializer.MessageJsonDeserializer
import io.qalipsis.api.messaging.deserializer.MessageStringDeserializer
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.netty.mqtt.MqttClientOptions
import io.qalipsis.plugins.netty.mqtt.spec.MqttQoS
import io.qalipsis.plugins.netty.mqtt.subscriber.MqttSubscribeConverter
import io.qalipsis.plugins.netty.mqtt.subscriber.MqttSubscribeIterativeReader
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


/**
 * @author Gabriel Moraes
 */
@Suppress("UNCHECKED_CAST")
@WithMockk
internal class MqttSubscribeStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<MqttSubscribeStepSpecificationConverter>() {

    @RelaxedMockK
    private lateinit var mockedClientOptions: MqttClientOptions

    override fun `should not support unexpected spec`() {
        Assertions.assertFalse(converter.support(relaxedMockk()))

    }

    override fun `should support expected spec`() {
        Assertions.assertTrue(converter.support(relaxedMockk<MqttSubscribeStepSpecificationImpl<*>>()))

    }

    @Test
    internal fun `should convert spec with name and topic`() = runBlockingTest {
        // given
        val deserializer = MessageStringDeserializer()
        val spec = MqttSubscribeStepSpecificationImpl(deserializer)
        spec.apply {
            name = "my-step"
            connect {
                host = "localhost"
            }
            concurrency(2)
            topicFilter("name1")
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        val spiedConverter = spyk(converter, recordPrivateCalls = true)
        val recordsConverter: DatasourceObjectConverter<MqttPublishMessage, out Any?> = relaxedMockk()

        every {
            spiedConverter["buildConverter"](
                eq("my-step"),
                refEq(spec.mqttSubscribeConfiguration.valueDeserializer),
                refEq(spec.mqttSubscribeConfiguration.metricsConfiguration)
            )
        } returns recordsConverter

        every { spiedConverter["buildClientOptions"](refEq(spec.mqttSubscribeConfiguration)) } returns mockedClientOptions

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<MqttSubscribeStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(IterativeDatasourceStep::class).all {
                prop("id").isEqualTo("my-step")
                prop("reader").isNotNull().isInstanceOf(MqttSubscribeIterativeReader::class).all {
                    prop("subscribeQoS").isEqualTo(MqttQoS.AT_LEAST_ONCE)
                    prop("concurrency").isEqualTo(2)
                    prop("topic").isEqualTo("name1")
                    prop("mqttClientOptions").isEqualTo(mockedClientOptions)
                }
                prop("processor").isNotNull().isInstanceOf(NoopDatasourceObjectProcessor::class)
                prop("converter").isNotNull().isSameAs(recordsConverter)
            }
        }
    }

    @Test
    internal fun `should convert spec without name but with topic`() = runBlockingTest {

        // given
        val deserializer = MessageStringDeserializer()
        val spec = MqttSubscribeStepSpecificationImpl(deserializer)
        spec.apply {
            connect {
                host = "localhost"
            }
            concurrency(2)
            topicFilter("name1")
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        val spiedConverter = spyk(converter)
        val recordsConverter: DatasourceObjectConverter<MqttPublishMessage, out Any?> = relaxedMockk()
        val stepIdSlot = slot<String>()


        every {
            spiedConverter["buildConverter"](
                capture(stepIdSlot),
                refEq(spec.mqttSubscribeConfiguration.valueDeserializer),
                refEq(spec.mqttSubscribeConfiguration.metricsConfiguration)
            )
        } returns recordsConverter

        every { spiedConverter["buildClientOptions"](refEq(spec.mqttSubscribeConfiguration)) } returns mockedClientOptions

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<MqttSubscribeStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(IterativeDatasourceStep::class).all {
                prop("id").isNotNull().isEqualTo(stepIdSlot.captured)
                prop("reader").isNotNull().isInstanceOf(MqttSubscribeIterativeReader::class).all {
                    prop("subscribeQoS").isEqualTo(MqttQoS.AT_LEAST_ONCE)
                    prop("concurrency").isEqualTo(2)
                    prop("topic").isEqualTo("name1")
                    prop("mqttClientOptions").isEqualTo(mockedClientOptions)
                }
                prop("processor").isNotNull().isInstanceOf(NoopDatasourceObjectProcessor::class)
                prop("converter").isNotNull().isSameAs(recordsConverter)
            }
        }
    }

    @Test
    internal fun `should build single converter`() {

        val monitoringConfiguration = MqttSubscriberMetricsConfiguration()
        val valueDeserializer = MessageStringDeserializer()

        // when
        val recordsConverter = converter.invokeInvisible<DatasourceObjectConverter<MqttPublishMessage, out Any?>>("buildConverter","my-step", valueDeserializer, monitoringConfiguration)

        // then
        assertThat(recordsConverter).isNotNull().isInstanceOf(MqttSubscribeConverter::class).all {
            prop("valueDeserializer").isSameAs(valueDeserializer)
            prop("consumedValueBytesCounter").isNull()
            prop("consumedRecordsCounter").isNull()
        }
    }

    @Test
    internal fun `should build single converter with json deserializer`() {

        val monitoringConfiguration = MqttSubscriberMetricsConfiguration()
        val jsonValueDeserializer = MessageJsonDeserializer(String::class)

        // when
        val recordsConverter = converter.invokeInvisible<DatasourceObjectConverter<MqttPublishMessage, out Any?>>("buildConverter","my-step", jsonValueDeserializer, monitoringConfiguration)

        // then
        assertThat(recordsConverter).isNotNull().isInstanceOf(MqttSubscribeConverter::class).all {
            prop("valueDeserializer").isSameAs(jsonValueDeserializer)
            prop("consumedValueBytesCounter").isNull()
            prop("consumedRecordsCounter").isNull()
        }
    }

    @Test
    internal fun `should build converter with value bytes counter`() {
        val monitoringConfiguration = MqttSubscriberMetricsConfiguration(receivedBytes = true)
        val valueDeserializer = MessageStringDeserializer()
        // when
        val recordsConverter = converter.invokeInvisible<DatasourceObjectConverter<MqttPublishMessage, out Any?>>("buildConverter","my-step", valueDeserializer, monitoringConfiguration)

        // then
        assertThat(recordsConverter).isNotNull().isInstanceOf(MqttSubscribeConverter::class).all {
            prop("valueDeserializer").isSameAs(valueDeserializer)
            prop("consumedValueBytesCounter").isNotNull().isInstanceOf(Counter::class)
            prop("consumedRecordsCounter").isNull()
        }
        verifyOnce {
            meterRegistry.counter("mqtt-subscribe-value-bytes", "step", "my-step")
        }
        confirmVerified(meterRegistry)
    }

    @Test
    internal fun `should build converter with records counter`() {
        val monitoringConfiguration = MqttSubscriberMetricsConfiguration(recordsCount = true)
        val valueDeserializer = MessageStringDeserializer()
        // when
        val recordsConverter = converter.invokeInvisible<DatasourceObjectConverter<MqttPublishMessage, out Any?>>("buildConverter","my-step", valueDeserializer, monitoringConfiguration)

        // then
        assertThat(recordsConverter).isNotNull().isInstanceOf(MqttSubscribeConverter::class).all {
            prop("valueDeserializer").isSameAs(valueDeserializer)
            prop("consumedValueBytesCounter").isNull()
            prop("consumedRecordsCounter").isNotNull().isInstanceOf(Counter::class)
        }
        verifyOnce {
            meterRegistry.counter("mqtt-subscribe-records", "step", "my-step")
        }
        confirmVerified(meterRegistry)
    }
}