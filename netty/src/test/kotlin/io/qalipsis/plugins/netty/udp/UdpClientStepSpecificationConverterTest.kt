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

package io.qalipsis.plugins.netty.udp

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import io.aerisconsulting.catadioptre.getProperty
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.udp.spec.UdpClientStepSpecification
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.coroutines.CoroutineContext

@Suppress("UNCHECKED_CAST")
internal class UdpClientStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<UdpClientStepSpecificationConverter>() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var eventLoopGroupSupplier: EventLoopGroupSupplier

    @RelaxedMockK
    private lateinit var ioCoroutineContext: CoroutineContext

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<UdpClientStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name and retry policy to step`() = testDispatcherProvider.runTest {
        // given
        val requestSpecification: suspend (ctx: StepContext<*, *>, input: Int) -> ByteArray =
            { _, _ -> ByteArray(1) { it.toByte() } }
        val spec = UdpClientStepSpecification<Int>()
        spec.apply {
            name = "my-step"
            retryPolicy = mockedRetryPolicy
            request(requestSpecification)
            monitoring {
                events = true
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<UdpClientStepSpecification<*>>)

        // then
        creationContext.createdStep!!.let {
            assertThat(it).all {
                isInstanceOf(UdpClientStep::class)
                prop("name").isEqualTo("my-step")
                prop("retryPolicy").isSameAs(mockedRetryPolicy)
                prop("eventLoopGroupSupplier").isSameAs(eventLoopGroupSupplier)
                prop("requestFactory").isSameAs(requestSpecification)
                prop("connectionConfiguration").isSameAs(spec.getProperty("connectionConfiguration"))
                prop("eventsLogger").isSameAs(eventsLogger)
                prop("meterRegistry").isNull()
            }
        }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step`() = testDispatcherProvider.runTest {
        // given
        val requestSpecification: suspend (ctx: StepContext<*, *>, input: Int) -> ByteArray =
            { _, _ -> ByteArray(1) { it.toByte() } }
        val spec = UdpClientStepSpecification<Int>()
        spec.apply {
            request(requestSpecification)
            monitoring {
                meters = true
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<UdpClientStepSpecification<*>>)

        // then
        creationContext.createdStep!!.let {
            assertThat(it).all {
                isInstanceOf(UdpClientStep::class)
                prop("name").isNotNull()
                prop("retryPolicy").isNull()
                prop("eventLoopGroupSupplier").isSameAs(eventLoopGroupSupplier)
                prop("requestFactory").isSameAs(requestSpecification)
                prop("connectionConfiguration").isSameAs(spec.getProperty("connectionConfiguration"))
                prop("eventsLogger").isNull()
                prop("meterRegistry").isSameAs(meterRegistry)
            }
        }
    }
}
