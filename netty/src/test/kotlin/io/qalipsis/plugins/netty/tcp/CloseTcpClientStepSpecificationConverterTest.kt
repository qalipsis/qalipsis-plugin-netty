package io.qalipsis.plugins.netty.tcp

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
import io.qalipsis.plugins.netty.tcp.spec.CloseTcpClientStepSpecification
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.coroutines.CoroutineContext

@Suppress("UNCHECKED_CAST")
internal class CloseTcpClientStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<CloseTcpClientStepSpecificationConverter>() {

    @RelaxedMockK
    lateinit var connectionOwner: TcpClientStep<Long, ByteArray>

    @RelaxedMockK
    private lateinit var ioCoroutineContext: CoroutineContext

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<CloseTcpClientStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner exists on the DAG`() =
        runBlockingTest {
            // given
            val spec = CloseTcpClientStepSpecification<Int>("my-previous-tcp-step")
            val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
            coEvery {
                directedAcyclicGraph.findStep("my-previous-tcp-step")
            } returns (connectionOwner to relaxedMockk { })

            // when
            converter.convert<String, Int>(creationContext as StepCreationContext<CloseTcpClientStepSpecification<*>>)

            // then
            creationContext.createdStep!!.let {
                assertThat(it).all {
                    isInstanceOf(CloseTcpClientStep::class)
                    prop("id").isNotNull()
                    prop("retryPolicy").isNull()
                    prop("ioCoroutineContext").isSameAs(ioCoroutineContext)
                    prop("connectionOwner").isSameAs(connectionOwner)
                }
            }

            verifyOnce { connectionOwner.keepOpen() }
        }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner exists on the DAG but several times decorated`() =
        runBlockingTest {
            // given
            val spec = CloseTcpClientStepSpecification<Int>("my-previous-tcp-step")
            val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
            val decorator = relaxedMockk<StepDecorator<*, *>> {
                every { decorated } returns relaxedMockk<StepDecorator<*, *>>() {
                    every { decorated } returns connectionOwner
                }
            }
            coEvery {
                directedAcyclicGraph.findStep("my-previous-tcp-step")
            } returns (decorator to relaxedMockk { })

            // when
            converter.convert<String, Int>(creationContext as StepCreationContext<CloseTcpClientStepSpecification<*>>)

            // then
            creationContext.createdStep!!.let {
                assertThat(it).all {
                    isInstanceOf(CloseTcpClientStep::class)
                    prop("id").isNotNull()
                    prop("retryPolicy").isNull()
                    prop("ioCoroutineContext").isSameAs(ioCoroutineContext)
                    prop("connectionOwner").isSameAs(connectionOwner)
                }
            }

            verifyOnce { connectionOwner.keepOpen() }
        }


    @Test
    internal fun `should convert spec to step when connection owner is indirectly referenced`() = runBlockingTest {
        // given
        val spec = CloseTcpClientStepSpecification<Int>("my-previous-kept-alive-tcp-step")
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val previousKeptAliveTcpClientStep = relaxedMockk<QueryTcpClientStep<String>>()
        every { previousKeptAliveTcpClientStep.connectionOwner } returns connectionOwner
        coEvery {
            directedAcyclicGraph.findStep("my-previous-kept-alive-tcp-step")
        } returns (previousKeptAliveTcpClientStep to relaxedMockk())

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<CloseTcpClientStepSpecification<*>>)

        // then
        creationContext.createdStep!!.let {
            assertThat(it).all {
                isInstanceOf(CloseTcpClientStep::class)
                prop("id").isNotNull()
                prop("retryPolicy").isNull()
                prop("ioCoroutineContext").isSameAs(ioCoroutineContext)
                prop("connectionOwner").isSameAs(connectionOwner)
            }
        }

        verifyOnce { connectionOwner.keepOpen() }
    }

    @Test
    internal fun `should convert spec to step when connection owner does not exist on the DAG`() = runBlockingTest {
        // given
        val spec = CloseTcpClientStepSpecification<Int>("my-previous-tcp-step")
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        coEvery { directedAcyclicGraph.findStep(any()) } returns null

        // when
        assertThrows<InvalidSpecificationException> {
            converter.convert<String, Int>(
                creationContext as StepCreationContext<CloseTcpClientStepSpecification<*>>)
        }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner exists but is of a different type`() =
        runBlockingTest {
            // given
            val spec = CloseTcpClientStepSpecification<Int>("my-previous-tcp-step")
            val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
            coEvery { directedAcyclicGraph.findStep(any()) } returns (relaxedMockk<Step<*, *>>() to relaxedMockk())

            // when
            assertThrows<InvalidSpecificationException> {
                converter.convert<String, Int>(
                    creationContext as StepCreationContext<CloseTcpClientStepSpecification<*>>)
            }
        }
}