package io.qalipsis.plugins.netty.tcp

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.tcp.spec.TcpClientStepSpecificationImpl
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.coroutines.CoroutineContext

@Suppress("UNCHECKED_CAST")
internal class TcpClientStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<TcpClientStepSpecificationConverter>() {

    @RelaxedMockK
    private lateinit var eventLoopGroupSupplier: EventLoopGroupSupplier

    @RelaxedMockK
    private lateinit var ioCoroutineContext: CoroutineContext

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<TcpClientStepSpecificationImpl<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name and retry policy to step`() = runBlockingTest {
        // given
        val requestSpecification: suspend (StepContext<*, *>, Int) -> ByteArray =
            { _, _ -> ByteArray(1) { it.toByte() } }
        val spec = TcpClientStepSpecificationImpl<Int>()
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
        converter.convert<String, Int>(creationContext as StepCreationContext<TcpClientStepSpecificationImpl<*>>)

        // then
        assertThat(creationContext.createdStep).isNotNull().isInstanceOf(SimpleTcpClientStep::class).all {
            prop(SimpleTcpClientStep<*>::id).isEqualTo("my-step")
            prop(SimpleTcpClientStep<*>::retryPolicy).isSameAs(mockedRetryPolicy)
            prop("eventLoopGroupSupplier").isSameAs(eventLoopGroupSupplier)
            prop("requestFactory").isSameAs(requestSpecification)
            prop("ioCoroutineContext").isSameAs(ioCoroutineContext)
            prop("clientConfiguration").isSameAs(spec.connectionConfiguration)
            prop("eventsLogger").isSameAs(eventsLogger)
            prop("meterRegistry").isNull()
        }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step`() = runBlockingTest {
        // given
        val requestSpecification: suspend (StepContext<*, *>, Int) -> ByteArray =
            { _, _ -> ByteArray(1) { it.toByte() } }
        val spec = TcpClientStepSpecificationImpl<Int>()
        spec.apply {
            request(requestSpecification)
            monitoring {
                meters = true
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<TcpClientStepSpecificationImpl<*>>)

        // then
        assertThat(creationContext.createdStep).isNotNull().isInstanceOf(SimpleTcpClientStep::class).all {
            prop(SimpleTcpClientStep<*>::id).isNotNull()
            prop(SimpleTcpClientStep<*>::retryPolicy).isNull()
            prop("eventLoopGroupSupplier").isSameAs(eventLoopGroupSupplier)
            prop("requestFactory").isSameAs(requestSpecification)
            prop("ioCoroutineContext").isSameAs(ioCoroutineContext)
            prop("clientConfiguration").isSameAs(spec.connectionConfiguration)
            prop("eventsLogger").isNull()
            prop("meterRegistry").isSameAs(meterRegistry)
        }
    }

    @Test
    internal fun `should convert spec with name and pool configuration`() = runBlockingTest {
        // given
        val requestSpecification: suspend (StepContext<*, *>, Int) -> ByteArray =
            { _, _ -> ByteArray(1) { it.toByte() } }
        val spec = TcpClientStepSpecificationImpl<Int>()
        spec.apply {
            name = "my-step"
            retryPolicy = mockedRetryPolicy
            request(requestSpecification)
            pool {
                size = 123
                checkHealthBeforeUse = false
            }

            monitoring {
                events = true
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<TcpClientStepSpecificationImpl<*>>)

        // then
        assertThat(creationContext.createdStep).isNotNull().isInstanceOf(PooledTcpClientStep::class).all {
            prop(PooledTcpClientStep<*>::id).isEqualTo("my-step")
            prop(PooledTcpClientStep<*>::retryPolicy).isSameAs(mockedRetryPolicy)
            prop("eventLoopGroupSupplier").isSameAs(eventLoopGroupSupplier)
            prop("requestFactory").isSameAs(requestSpecification)
            prop("ioCoroutineContext").isSameAs(ioCoroutineContext)
            prop("clientConfiguration").isSameAs(spec.connectionConfiguration)
            prop("poolConfiguration").isSameAs(spec.poolConfiguration)
            prop("eventsLogger").isSameAs(eventsLogger)
            prop("meterRegistry").isNull()
        }
    }

}
