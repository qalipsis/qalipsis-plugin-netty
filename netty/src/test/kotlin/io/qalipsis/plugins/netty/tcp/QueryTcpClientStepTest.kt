package io.qalipsis.plugins.netty.tcp

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.SocketStepException
import io.qalipsis.plugins.netty.socket.SocketStepRequestException
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.charset.StandardCharsets

@WithMockk
internal class QueryTcpClientStepTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    lateinit var simpleTcpClientStep: SimpleTcpClientStep<*>

    @Test
    @Timeout(5L)
    internal fun `should call the actual tcp step with events and meters`() = testDispatcherProvider.runTest {
        val input = "This is a test"
        val request = input.toByteArray(StandardCharsets.UTF_8)
        val step =
            QueryTcpClientStep<String>(
                "",
                null,
                this.coroutineContext,
                simpleTcpClientStep,
                { _, _ -> request },
                eventsLogger,
                meterRegistry
            )
        val ctx =
            StepTestHelper.createStepContext<String, RequestResult<String, ByteArray, *>>(input = "This is a test")
        val monitoringCollector = slot<StepContextBasedSocketMonitoringCollector>()

        val response = ByteArray(0)
        coEvery {
            simpleTcpClientStep.execute(
                capture(monitoringCollector),
                refEq(ctx),
                eq("This is a test"),
                refEq(request)
            )
        } returns response

        step.execute(ctx)

        val result = (ctx.output as Channel<StepContext.StepOutputRecord<RequestResult<String, ByteArray, *>>>).receive().value
        assertThat(result).all {
            prop(RequestResult<String, ByteArray, *>::input).isEqualTo("This is a test")
            prop(RequestResult<String, ByteArray, *>::isSuccess).isTrue()
            prop(RequestResult<String, ByteArray, *>::isFailure).isFalse()
            prop(RequestResult<String, ByteArray, *>::sendingFailure).isNull()
            prop(RequestResult<String, ByteArray, *>::failure).isNull()
            prop(RequestResult<String, ByteArray, *>::cause).isNull()
            prop(RequestResult<String, ByteArray, *>::response).isSameAs(response)
            prop(RequestResult<String, ByteArray, *>::meters).isSameAs(monitoringCollector.captured.meters)
        }

        coVerify {
            simpleTcpClientStep.execute(any(), refEq(ctx), eq("This is a test"), refEq(request))
        }
        assertThat(monitoringCollector.captured).all {
            prop("stepContext").isSameAs(ctx)
            prop("eventsLogger").isSameAs(eventsLogger)
            prop("meterRegistry").isSameAs(meterRegistry)
        }

        confirmVerified(simpleTcpClientStep)
    }

    @Test
    @Timeout(5L)
    internal fun `should call the actual tcp step without events and meters and rethrow the exception`() =
        testDispatcherProvider.runTest {
            val input = "This is a test"
            val request = input.toByteArray(StandardCharsets.UTF_8)
            val step = QueryTcpClientStep<String>(
                "",
                null,
                this.coroutineContext,
                simpleTcpClientStep,
                { _, _ -> request },
                null,
                null
            )
            val ctx =
                StepTestHelper.createStepContext<String, RequestResult<String, ByteArray, *>>(input = input)
            val monitoringCollector = slot<StepContextBasedSocketMonitoringCollector>()
            val tcpResult = ConnectionAndRequestResult<String, ByteArray>(
                false,
                relaxedMockk(),
                relaxedMockk(),
                relaxedMockk(),
                relaxedMockk(),
                "",
                null,
                relaxedMockk()
            )
            coEvery {
                simpleTcpClientStep.execute(
                    capture(monitoringCollector),
                    refEq(ctx),
                    eq("This is a test"),
                    refEq(request)
                )
            } throws SocketStepException(tcpResult)

            val result = assertThrows<SocketStepRequestException> {
                step.execute(ctx)
            }.result

            assertThat(result).all {
                prop(RequestResult<*, *, *>::input).isEqualTo("This is a test")
                prop(RequestResult<*, *, *>::isSuccess).isFalse()
                prop(RequestResult<*, *, *>::isFailure).isTrue()
                prop(RequestResult<*, *, *>::cause).isSameAs(tcpResult.cause)
                prop(RequestResult<*, *, *>::sendingFailure).isSameAs(tcpResult.sendingFailure)
                prop(RequestResult<*, *, *>::failure).isSameAs(tcpResult.failure)
                prop(RequestResult<*, *, *>::response).isNull()
                prop(RequestResult<*, *, *>::meters).isSameAs(tcpResult.meters)
            }

            coVerify {
                simpleTcpClientStep.execute(any(), refEq(ctx), eq("This is a test"), refEq(request))
            }
            assertThat(monitoringCollector.captured).all {
                prop("stepContext").isSameAs(ctx)
                prop("eventsLogger").isNull()
                prop("meterRegistry").isNull()
            }

            confirmVerified(simpleTcpClientStep)
        }

}
