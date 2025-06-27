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
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Timer
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.SocketException
import io.qalipsis.plugins.netty.socket.SocketRequestException
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.BeforeEach
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
    lateinit var meterRegistry: CampaignMeterRegistry

    @RelaxedMockK
    lateinit var simpleTcpClientStep: SimpleTcpClientStep<*>

    @BeforeEach
    fun setUp() {
        every {
            meterRegistry.counter(
                scenarioName = any<String>(),
                stepName = any<String>(),
                name = any<String>(),
                tags = any<Map<String, String>>()
            )
        } returns relaxedMockk<Counter> {
            every { report(any()) } returns this
        }
        every {
            meterRegistry.timer(
                scenarioName = any<String>(),
                stepName = any<String>(),
                name = any<String>(),
                tags = any<Map<String, String>>()
            )
        } returns relaxedMockk<Timer> {
            every { report(any()) } returns this
        }
    }


    @Test
    @Timeout(5L)
    internal fun `should call the actual tcp step with events and meters`() = testDispatcherProvider.runTest {
        val input = "This is a test"
        val request = input.toByteArray(StandardCharsets.UTF_8)
        val step =
            QueryTcpClientStep<String>(
                "",
                null,
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
            } throws SocketException(tcpResult)

            val result = assertThrows<SocketRequestException> {
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
