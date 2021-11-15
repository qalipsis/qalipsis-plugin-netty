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
import io.qalipsis.plugins.netty.tcp.spec.QueryTcpClientStepSpecification
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
internal class QueryTcpClientStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<QueryTcpClientStepSpecificationConverter>() {

    @RelaxedMockK
    private lateinit var ioCoroutineContext: CoroutineContext

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
        runBlockingTest {
            // given
            val requestSpecification: suspend (ctx: StepContext<*, *>, input: Int) -> ByteArray =
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
                assertThat(it).all {
                    isInstanceOf(QueryTcpClientStep::class)
                    prop("id").isEqualTo("my-step")
                    prop("retryPolicy").isSameAs(mockedRetryPolicy)
                    prop("ioCoroutineContext").isSameAs(ioCoroutineContext)
                    prop("requestFactory").isSameAs(requestSpecification)
                    prop("connectionOwner").isSameAs(connectionOwner)
                    prop("eventsLogger").isSameAs(eventsLogger)
                    prop("meterRegistry").isNull()
                }
            }
        }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner exists on the DAG`() =
        runBlockingTest {
            // given
            val requestSpecification: suspend (ctx: StepContext<*, *>, input: Int) -> ByteArray =
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
                assertThat(it).all {
                    isInstanceOf(QueryTcpClientStep::class)
                    prop("id").isNotNull()
                    prop("retryPolicy").isNull()
                    prop("ioCoroutineContext").isSameAs(ioCoroutineContext)
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
        runBlockingTest {
            // given
            val requestSpecification: suspend (ctx: StepContext<*, *>, input: Int) -> ByteArray =
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
                assertThat(it).all {
                    isInstanceOf(QueryTcpClientStep::class)
                    prop("id").isNotNull()
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
        runBlockingTest {
            // given
            val requestSpecification: suspend (ctx: StepContext<*, *>, input: Int) -> ByteArray =
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
        runBlockingTest {
            // given
            val requestSpecification: suspend (ctx: StepContext<*, *>, input: Int) -> ByteArray =
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
