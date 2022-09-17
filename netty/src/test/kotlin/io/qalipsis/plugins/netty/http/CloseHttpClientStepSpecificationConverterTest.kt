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

package io.qalipsis.plugins.netty.http

import assertk.all
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepDecorator
import io.qalipsis.plugins.netty.http.spec.CloseHttpClientStepSpecification
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.coroutines.CoroutineContext

@Suppress("UNCHECKED_CAST")
internal class CloseHttpClientStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<CloseHttpClientStepSpecificationConverter>() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var connectionOwner: HttpClientStep<Long, ByteArray>

    @RelaxedMockK
    private lateinit var ioCoroutineContext: CoroutineContext

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<CloseHttpClientStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner exists on the DAG`() =
        testDispatcherProvider.runTest {
            // given
            val spec = CloseHttpClientStepSpecification<Int>("my-previous-http-step")
            val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
            coEvery {
                directedAcyclicGraph.findStep("my-previous-http-step")
            } returns (connectionOwner to relaxedMockk { })

            // when
            converter.convert<String, Int>(creationContext as StepCreationContext<CloseHttpClientStepSpecification<*>>)

            // then
            creationContext.createdStep!!.let {
                assertThat(it).all {
                    isInstanceOf(CloseHttpClientStep::class)
                    prop("name").isNotNull()
                    prop("retryPolicy").isNull()
                    prop("ioCoroutineContext").isSameAs(ioCoroutineContext)
                    prop("connectionOwner").isSameAs(connectionOwner)
                }
            }

            verifyOnce { connectionOwner.keepOpen() }
        }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner exists on the DAG but several times decorated`() =
        testDispatcherProvider.runTest {
            // given
            val spec = CloseHttpClientStepSpecification<Int>("my-previous-http-step")
            val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
            val decorator = relaxedMockk<StepDecorator<*, *>> {
                every { decorated } returns relaxedMockk<StepDecorator<*, *>>() {
                    every { decorated } returns connectionOwner
                }
            }
            coEvery {
                directedAcyclicGraph.findStep("my-previous-http-step")
            } returns (decorator to relaxedMockk { })

            // when
            converter.convert<String, Int>(creationContext as StepCreationContext<CloseHttpClientStepSpecification<*>>)

            // then
            creationContext.createdStep!!.let {
                assertThat(it).all {
                    isInstanceOf(CloseHttpClientStep::class)
                    prop("name").isNotNull()
                    prop("retryPolicy").isNull()
                    prop("ioCoroutineContext").isSameAs(ioCoroutineContext)
                    prop("connectionOwner").isSameAs(connectionOwner)
                }
            }

            verifyOnce { connectionOwner.keepOpen() }
        }


    @Test
    internal fun `should convert spec to step when connection owner is indirectly referenced`() = testDispatcherProvider.runTest {
        // given
        val spec = CloseHttpClientStepSpecification<Int>("my-previous-kept-alive-http-step")
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val previousKeptAliveHttpClientStep = relaxedMockk<QueryHttpClientStep<String, Int>>()
        every { previousKeptAliveHttpClientStep.connectionOwner } returns connectionOwner
        coEvery {
            directedAcyclicGraph.findStep("my-previous-kept-alive-http-step")
        } returns (previousKeptAliveHttpClientStep to relaxedMockk())

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<CloseHttpClientStepSpecification<*>>)

        // then
        creationContext.createdStep!!.let {
            assertThat(it).all {
                isInstanceOf(CloseHttpClientStep::class)
                prop("name").isNotNull()
                prop("retryPolicy").isNull()
                prop("ioCoroutineContext").isSameAs(ioCoroutineContext)
                prop("connectionOwner").isSameAs(connectionOwner)
            }
        }

        verifyOnce { connectionOwner.keepOpen() }
    }

    @Test
    internal fun `should convert spec to step when connection owner does not exist on the DAG`() = testDispatcherProvider.runTest {
        // given
        val spec = CloseHttpClientStepSpecification<Int>("my-previous-http-step")
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        coEvery { directedAcyclicGraph.findStep(any()) } returns null

        // when
        org.junit.jupiter.api.assertThrows<InvalidSpecificationException> {
            converter.convert<String, Int>(
                creationContext as StepCreationContext<CloseHttpClientStepSpecification<*>>
            )
        }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner exists but is of a different type`() =
        testDispatcherProvider.runTest {
            // given
            val spec = CloseHttpClientStepSpecification<Int>("my-previous-http-step")
            val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
            coEvery { directedAcyclicGraph.findStep(any()) } returns (relaxedMockk<Step<*, *>>() to relaxedMockk())

            // when
            org.junit.jupiter.api.assertThrows<InvalidSpecificationException> {
                converter.convert<String, Int>(
                    creationContext as StepCreationContext<CloseHttpClientStepSpecification<*>>
                )
            }
        }
}

