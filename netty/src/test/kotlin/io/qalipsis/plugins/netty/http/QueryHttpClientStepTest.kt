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
import io.netty.handler.codec.http.HttpResponse
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Timer
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.http.request.HttpRequest
import io.qalipsis.plugins.netty.http.response.ResponseConverter
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.SocketException
import io.qalipsis.plugins.netty.socket.SocketRequestException
import io.qalipsis.plugins.netty.tcp.ConnectionAndRequestResult
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
import io.qalipsis.plugins.netty.http.response.HttpResponse as QalipsisResponse

@Suppress("UNCHECKED_CAST")
@WithMockk
internal class QueryHttpClientStepTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var responseConverter: ResponseConverter<String>

    @RelaxedMockK
    lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    lateinit var meterRegistry: CampaignMeterRegistry

    @RelaxedMockK
    lateinit var simpleHttpClientStep: SimpleHttpClientStep<*, *>

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
    internal fun `should call the actual http step with events and meters`() = testDispatcherProvider.runTest {
        val request = relaxedMockk<HttpRequest<*>>()
        val requestBlock: suspend HttpRequestBuilder.(StepContext<*, *>, String) -> HttpRequest<*> = { _, _ -> request }
        val step = QueryHttpClientStep(
            "",
            null,
            simpleHttpClientStep,
            requestBlock,
            responseConverter,
            eventsLogger,
            meterRegistry
        )
        val ctx =
            StepTestHelper.createStepContext<String, RequestResult<String, QalipsisResponse<String>, *>>(input = "This is a test")
        val monitoringCollector = slot<StepContextBasedSocketMonitoringCollector>()

        val response = relaxedMockk<HttpResponse>()
        val convertedResponse: io.qalipsis.plugins.netty.http.response.HttpResponse<String> = relaxedMockk()
        every { responseConverter.convert(response) } returns convertedResponse
        coEvery {
            simpleHttpClientStep.execute(
                capture(monitoringCollector),
                refEq(ctx),
                eq("This is a test"),
                refEq(request)
            )
        } returns response

        step.execute(ctx)

        val result = (ctx.output as Channel<StepContext.StepOutputRecord<RequestResult<String, HttpResponse, *>>>).receive().value
        assertThat(result).all {
            prop(RequestResult<String, HttpResponse, *>::input).isEqualTo("This is a test")
            prop(RequestResult<String, HttpResponse, *>::isSuccess).isTrue()
            prop(RequestResult<String, HttpResponse, *>::isFailure).isFalse()
            prop(RequestResult<String, HttpResponse, *>::sendingFailure).isNull()
            prop(RequestResult<String, HttpResponse, *>::failure).isNull()
            prop(RequestResult<String, HttpResponse, *>::cause).isNull()
            prop(RequestResult<String, HttpResponse, *>::response).isSameAs(convertedResponse)
            prop(RequestResult<String, HttpResponse, *>::meters).isSameAs(monitoringCollector.captured.meters)
        }

        coVerify {
            simpleHttpClientStep.execute(any(), refEq(ctx), eq("This is a test"), refEq(request))
        }
        assertThat(monitoringCollector.captured).all {
            prop("stepContext").isSameAs(ctx)
            prop("eventsLogger").isSameAs(eventsLogger)
            prop("meterRegistry").isSameAs(meterRegistry)
        }

        confirmVerified(simpleHttpClientStep)
    }

    @Test
    @Timeout(5L)
    internal fun `should call the actual http step without events and meters and rethrow the exception`() =
        testDispatcherProvider.runTest {
            val request = relaxedMockk<HttpRequest<*>>()
            val requestBlock: suspend HttpRequestBuilder.(StepContext<*, *>, String) -> HttpRequest<*> =
                { _, _ -> request }
            val step = QueryHttpClientStep(
                "",
                null,
                simpleHttpClientStep,
                requestBlock,
                responseConverter,
                null,
                null
            )
            val ctx =
                StepTestHelper.createStepContext<String, RequestResult<String, QalipsisResponse<String>, *>>(input = "This is a test")
            val monitoringCollector = slot<StepContextBasedSocketMonitoringCollector>()
            val httpResult = ConnectionAndRequestResult<String, HttpResponse>(
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
                simpleHttpClientStep.execute(
                    capture(monitoringCollector),
                    refEq(ctx),
                    eq("This is a test"),
                    refEq(request)
                )
            } throws SocketException(httpResult)

            val result = assertThrows<SocketRequestException> {
                step.execute(ctx)
            }.result

            assertThat(result).all {
                prop(RequestResult<*, *, *>::input).isEqualTo("This is a test")
                prop(RequestResult<*, *, *>::isSuccess).isFalse()
                prop(RequestResult<*, *, *>::isFailure).isTrue()
                prop(RequestResult<*, *, *>::cause).isSameAs(httpResult.cause)
                prop(RequestResult<*, *, *>::sendingFailure).isSameAs(httpResult.sendingFailure)
                prop(RequestResult<*, *, *>::failure).isSameAs(httpResult.failure)
                prop(RequestResult<*, *, *>::response).isNull()
                prop(RequestResult<*, *, *>::meters).isSameAs(httpResult.meters)
            }

            coVerify {
                simpleHttpClientStep.execute(any(), refEq(ctx), eq("This is a test"), refEq(request))
            }
            assertThat(monitoringCollector.captured).all {
                prop("stepContext").isSameAs(ctx)
                prop("eventsLogger").isNull()
                prop("meterRegistry").isNull()
            }

            confirmVerified(simpleHttpClientStep)
        }

}
