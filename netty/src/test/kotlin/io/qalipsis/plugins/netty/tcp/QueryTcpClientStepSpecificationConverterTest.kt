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

package io.qalipsis.plugins.netty.tcp

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.plugins.netty.ByteArrayRequestBuilder
import io.qalipsis.plugins.netty.tcp.spec.QueryTcpClientStepSpecification
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

@Suppress("UNCHECKED_CAST")
internal class QueryTcpClientStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<QueryTcpClientStepSpecificationConverter>() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var connectionOwner: TcpClientStep<Long, *>

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<QueryTcpClientStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name and retry policy to step when connection owner exists on the DAG`() =
        testDispatcherProvider.runTest {
            // given
            val requestSpecification: suspend ByteArrayRequestBuilder.(ctx: StepContext<*, *>, input: Int) -> ByteArray =
                { _, _ -> ByteArray(1) { it.toByte() } }
            val spec = QueryTcpClientStepSpecification<Int>("my-previous-tcp-step")
            spec.apply {
                name = "my-step"
                retryPolicy = mockedRetryPolicy
                request(requestSpecification)
                monitoring {
                    events = true
                }
            }
            val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
            coEvery {
                directedAcyclicGraph.findStep("my-previous-tcp-step")
            } returns (connectionOwner to relaxedMockk())

            // when
            converter.convert<String, Int>(
                creationContext as StepCreationContext<QueryTcpClientStepSpecification<*>>
            )

            // then
            creationContext.createdStep!!.let {
                assertThat(it).isInstanceOf(QueryTcpClientStep::class).all {
                    prop("name").isEqualTo("my-step")
                    prop("retryPolicy").isSameAs(mockedRetryPolicy)
                    prop("requestFactory").isSameAs(requestSpecification)
                    prop("connectionOwner").isSameAs(connectionOwner)
                    prop("eventsLogger").isSameAs(eventsLogger)
                    prop("meterRegistry").isNull()
                }
            }
        }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner exists on the DAG`() =
        testDispatcherProvider.runTest {
            // given
            val requestSpecification: suspend ByteArrayRequestBuilder.(ctx: StepContext<*, *>, input: Int) -> ByteArray =
                { _, _ -> ByteArray(1) { it.toByte() } }
            val spec = QueryTcpClientStepSpecification<Int>("my-previous-tcp-step")
            spec.apply {
                request(requestSpecification)
                monitoring {
                    meters = true
                }
            }
            val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
            coEvery {
                directedAcyclicGraph.findStep("my-previous-tcp-step")
            } returns (connectionOwner to relaxedMockk())

            // when
            converter.convert<String, Int>(
                creationContext as StepCreationContext<QueryTcpClientStepSpecification<*>>
            )

            // then
            creationContext.createdStep!!.let {
                assertThat(it).isInstanceOf(QueryTcpClientStep::class).all {
                    prop("name").isNotNull()
                    prop("retryPolicy").isNull()
                    prop("requestFactory").isSameAs(requestSpecification)
                    prop("connectionOwner").isSameAs(connectionOwner)
                    prop("eventsLogger").isNull()
                    prop("meterRegistry").isSameAs(meterRegistry)
                }
            }

            verifyOnce { connectionOwner.addUsage() }
        }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner is indirectly referenced`() =
        testDispatcherProvider.runTest {
            // given
            val requestSpecification: suspend ByteArrayRequestBuilder.(ctx: StepContext<*, *>, input: Int) -> ByteArray =
                { _, _ -> ByteArray(1) { it.toByte() } }
            val spec = QueryTcpClientStepSpecification<Int>("my-previous-kept-alive-tcp-step")
            spec.apply {
                request(requestSpecification)
                monitoring {
                    events = true
                    meters = true
                }
            }
            val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
            val previousKeptAliveTcpClientStep = relaxedMockk<QueryTcpClientStep<String>>()
            every { previousKeptAliveTcpClientStep.connectionOwner } returns connectionOwner
            coEvery {
                directedAcyclicGraph.findStep("my-previous-kept-alive-tcp-step")
            } returns (previousKeptAliveTcpClientStep to relaxedMockk())

            // when
            converter.convert<String, Int>(
                creationContext as StepCreationContext<QueryTcpClientStepSpecification<*>>
            )

            // then
            creationContext.createdStep!!.let {
                assertThat(it).isInstanceOf(QueryTcpClientStep::class).all {
                    prop("name").isNotNull()
                    prop("retryPolicy").isNull()
                    prop("requestFactory").isSameAs(requestSpecification)
                    prop("connectionOwner").isSameAs(connectionOwner)
                    prop("eventsLogger").isSameAs(eventsLogger)
                    prop("meterRegistry").isSameAs(meterRegistry)
                }
            }

            verifyOnce { connectionOwner.addUsage() }
        }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner does not exist on the DAG`() =
        testDispatcherProvider.runTest {
            // given
            val requestSpecification: suspend ByteArrayRequestBuilder.(ctx: StepContext<*, *>, input: Int) -> ByteArray =
                { _, _ -> ByteArray(1) { it.toByte() } }
            val spec = QueryTcpClientStepSpecification<Int>("my-previous-tcp-step")
            spec.apply {
                request(requestSpecification)
            }
            val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
            coEvery { directedAcyclicGraph.findStep(any()) } returns null

            // when
            assertThrows<InvalidSpecificationException> {
                converter.convert<String, Int>(
                    creationContext as StepCreationContext<QueryTcpClientStepSpecification<*>>
                )
            }
        }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner exists but is of a different type`() =
        testDispatcherProvider.runTest {
            // given
            val requestSpecification: suspend ByteArrayRequestBuilder.(ctx: StepContext<*, *>, input: Int) -> ByteArray =
                { _, _ -> ByteArray(1) { it.toByte() } }
            val spec = QueryTcpClientStepSpecification<Int>("my-previous-tcp-step")
            spec.apply {
                request(requestSpecification)
            }
            val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
            coEvery { directedAcyclicGraph.findStep(any()) } returns (relaxedMockk<Step<*, *>>() to relaxedMockk())

            // when
            assertThrows<InvalidSpecificationException> {
                converter.convert<String, Int>(
                    creationContext as StepCreationContext<QueryTcpClientStepSpecification<*>>
                )
            }
        }
}
