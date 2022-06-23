package io.qalipsis.plugins.netty.mqtt.subscriber.spec

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import io.aerisconsulting.catadioptre.invokeInvisible
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.netty.handler.codec.mqtt.MqttPublishMessage
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.messaging.deserializer.MessageJsonDeserializer
import io.qalipsis.api.messaging.deserializer.MessageStringDeserializer
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.netty.mqtt.MqttClientOptions
import io.qalipsis.plugins.netty.mqtt.spec.MqttQoS
import io.qalipsis.plugins.netty.mqtt.subscriber.MqttSubscribeConverter
import io.qalipsis.plugins.netty.mqtt.subscriber.MqttSubscribeIterativeReader
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension


/**
 * @author Gabriel Moraes
 */
@Suppress("UNCHECKED_CAST")
@WithMockk
internal class MqttSubscribeStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<MqttSubscribeStepSpecificationConverter>() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var mockedClientOptions: MqttClientOptions

    override fun `should not support unexpected spec`() {
        Assertions.assertFalse(converter.support(relaxedMockk()))

    }

    override fun `should support expected spec`() {
        Assertions.assertTrue(converter.support(relaxedMockk<MqttSubscribeStepSpecificationImpl<*>>()))

    }

    @Test
    internal fun `should convert spec with name and topic`() = testDispatcherProvider.runTest {
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
                refEq(spec.mqttSubscribeConfiguration.valueDeserializer),
                refEq(spec.monitoringConfig)
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
                prop("name").isEqualTo("my-step")
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
    internal fun `should convert spec without name but with topic`() = testDispatcherProvider.runTest {

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

        every {
            spiedConverter["buildConverter"](
                refEq(spec.mqttSubscribeConfiguration.valueDeserializer),
                refEq(spec.monitoringConfig)
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
                prop("name").isNotNull()
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

        val monitoringConfiguration = StepMonitoringConfiguration()
        val valueDeserializer = MessageStringDeserializer()

        // when
        val recordsConverter = converter.invokeInvisible<DatasourceObjectConverter<MqttPublishMessage, out Any?>>(
            "buildConverter",
            valueDeserializer,
            monitoringConfiguration
        )

        // then
        assertThat(recordsConverter).isNotNull().isInstanceOf(MqttSubscribeConverter::class).all {
            prop("valueDeserializer").isSameAs(valueDeserializer)
            prop("meterRegistry").isNull()
            prop("eventsLogger").isNull()
        }
    }

    @Test
    internal fun `should build single converter with json deserializer`() {

        val monitoringConfiguration = StepMonitoringConfiguration()
        val jsonValueDeserializer = MessageJsonDeserializer(String::class)

        // when
        val recordsConverter = converter.invokeInvisible<DatasourceObjectConverter<MqttPublishMessage, out Any?>>(
            "buildConverter",
            jsonValueDeserializer,
            monitoringConfiguration
        )

        // then
        assertThat(recordsConverter).isNotNull().isInstanceOf(MqttSubscribeConverter::class).all {
            prop("valueDeserializer").isSameAs(jsonValueDeserializer)
            prop("meterRegistry").isNull()
            prop("eventsLogger").isNull()
        }
    }

    @Test
    internal fun `should build converter with monitoring`() {
        val monitoringConfiguration = StepMonitoringConfiguration(meters = true)
        val valueDeserializer = MessageStringDeserializer()
        // when
        val recordsConverter = converter.invokeInvisible<DatasourceObjectConverter<MqttPublishMessage, out Any?>>(
            "buildConverter",
            valueDeserializer,
            monitoringConfiguration
        )

        // then
        assertThat(recordsConverter).isNotNull().isInstanceOf(MqttSubscribeConverter::class).all {
            prop("valueDeserializer").isSameAs(valueDeserializer)
            prop("meterRegistry").isNotNull().isInstanceOf(MeterRegistry::class)
            prop("eventsLogger").isNull()
        }
        confirmVerified(meterRegistry)
    }

    @Test
    internal fun `should build converter with logger`() {
        val monitoringConfiguration = StepMonitoringConfiguration(events = true)
        val valueDeserializer = MessageStringDeserializer()
        // when
        val recordsConverter = converter.invokeInvisible<DatasourceObjectConverter<MqttPublishMessage, out Any?>>(
            "buildConverter",
            valueDeserializer,
            monitoringConfiguration
        )

        // then
        assertThat(recordsConverter).isNotNull().isInstanceOf(MqttSubscribeConverter::class).all {
            prop("valueDeserializer").isSameAs(valueDeserializer)
            prop("meterRegistry").isNull()
            prop("eventsLogger").isNotNull().isInstanceOf(EventsLogger::class)
        }
        confirmVerified(meterRegistry)
    }
}