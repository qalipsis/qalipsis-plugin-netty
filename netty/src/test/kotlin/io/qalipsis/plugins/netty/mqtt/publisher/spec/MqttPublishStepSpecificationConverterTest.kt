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
import io.mockk.spyk
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.mqtt.MqttClientOptions
import io.qalipsis.plugins.netty.mqtt.publisher.MqttPublishStep
import io.qalipsis.plugins.netty.mqtt.publisher.MqttPublishStepSpecificationConverter
import io.qalipsis.plugins.netty.mqtt.spec.MqttVersion
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
@WithMockk
@Suppress("UNCHECKED_CAST")
internal class MqttPublishStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<MqttPublishStepSpecificationConverter>() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var mockedClientOptions: MqttClientOptions

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
    internal fun `should convert spec with name and retry policy`() = testDispatcherProvider.runTest {
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
                prop("name").isEqualTo("my-step")
                prop("mqttClientOptions").isEqualTo(mockedClientOptions)
                prop("eventLoopGroupSupplier").isSameAs(eventLoopGroupSupplier)
                prop("retryPolicy").isNotNull()
                prop("recordsFactory").isNotNull()
            }
        }
    }


    @Test
    internal fun `should convert spec without name and retry policy`() = testDispatcherProvider.runTest {
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
            monitoring {
                events = false
                meters = true
            }
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
                prop("name").isNotNull().isEqualTo("")
                prop("mqttClientOptions").isEqualTo(mockedClientOptions)
                prop("eventLoopGroupSupplier").isSameAs(eventLoopGroupSupplier)
                prop("retryPolicy").isNull()
                prop("recordsFactory").isNotNull()
                prop("meterRegistry").isNotNull().isSameAs(meterRegistry)
                prop("eventsLogger").isNull()
            }
        }
    }


}
