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
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.key
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.getProperty
import io.aerisconsulting.catadioptre.setProperty
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.mockk.spyk
import io.netty.channel.EventLoopGroup
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponse
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Timer
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.exceptions.ClosedClientException
import io.qalipsis.plugins.netty.http.client.MultiSocketHttpClient
import io.qalipsis.plugins.netty.http.request.HttpRequest
import io.qalipsis.plugins.netty.http.request.SimpleHttpRequest
import io.qalipsis.plugins.netty.http.response.ResponseConverter
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.tcp.ConnectionAndRequestResult
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import io.qalipsis.plugins.netty.http.response.HttpResponse as QalipsisHttpResponse


@WithMockk
internal class SimpleHttpClientStepTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var responseConverter: ResponseConverter<String>

    @RelaxedMockK
    private lateinit var connectionConfiguration: HttpClientConfiguration

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var meterRegistry: CampaignMeterRegistry

    @RelaxedMockK
    private lateinit var workerGroupSupplier: EventLoopGroupSupplier

    @RelaxedMockK
    private lateinit var workerGroup: EventLoopGroup

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
    fun `should change the connection configuration to keep open`() = testDispatcherProvider.runTest {
        // given
        val config = HttpClientConfiguration().apply { keepConnectionAlive = false }
        val step = SimpleHttpClientStep<String, String>(
            "my-step",
            null,
            { _, _ -> SimpleHttpRequest(HttpMethod.GET, "/") },
            config,
            workerGroupSupplier,
            responseConverter,
            eventsLogger,
            meterRegistry
        )

        // when
        step.keepOpen()

        // then
        assertThat(config.keepConnectionAlive).isTrue()
    }

    @Test
    fun `should make the step running after start`() = testDispatcherProvider.runTest {
        // given
        val step = SimpleHttpClientStep<String, String>(
            "my-step",
            null,
            { _, _ -> SimpleHttpRequest(HttpMethod.GET, "/") },
            connectionConfiguration,
            workerGroupSupplier,
            responseConverter,
            eventsLogger,
            meterRegistry
        )
        every { workerGroupSupplier.getGroup() } returns workerGroup

        // when
        step.start(relaxedMockk())

        // then
        verifyOnce {
            workerGroupSupplier.getGroup()
        }
        assertThat(step).all {
            prop("running").isEqualTo(true)
            prop("workerGroup").isSameAs(workerGroup)
        }
        confirmVerified(workerGroup)
    }

    @Test
    fun `should close all the clients at stop`() = testDispatcherProvider.runTest {
        // given
        val step = SimpleHttpClientStep<String, String>(
            "my-step",
            null,
            { _, _ -> SimpleHttpRequest(HttpMethod.GET, "/") },
            connectionConfiguration,
            workerGroupSupplier,
            responseConverter,
            eventsLogger,
            meterRegistry
        )
        step.setProperty("running", true)
        val client1 = relaxedMockk<MultiSocketHttpClient>()
        val client2 = relaxedMockk<MultiSocketHttpClient>()
        val client3 = relaxedMockk<MultiSocketHttpClient>()
        val client4 = relaxedMockk<MultiSocketHttpClient>()
        step.getProperty<MutableMap<MinionId, Channel<MultiSocketHttpClient>>>("clients").apply {
            put("1", Channel<MultiSocketHttpClient>(1).apply { trySend(client1).getOrThrow() })
            put("2", Channel<MultiSocketHttpClient>(1).apply { trySend(client2).getOrThrow() })
        }
        step.getProperty<MutableMap<MinionId, MultiSocketHttpClient>>("clientsInUse").apply {
            put("1", client3)
            put("2", client4)
        }
        step.setProperty("workerGroup", workerGroup)

        // when
        step.stop(relaxedMockk())

        // then
        coVerifyOnce {
            client1.close()
            client2.close()
            client3.close()
            client4.close()
            workerGroup.shutdownGracefully()
        }
        assertThat(step).all {
            typedProp<Map<MinionId, Channel<MultiSocketHttpClient>>>("clients").isEmpty()
            typedProp<Map<MinionId, MultiSocketHttpClient>>("clientsInUse").isEmpty()
        }
        confirmVerified(workerGroup)
    }

    @Test
    fun `should execute the request from the step context`() = testDispatcherProvider.run {
        // given
        val response = relaxedMockk<HttpResponse>()
        val request = SimpleHttpRequest(HttpMethod.GET, "/")
        val requestFactory: suspend HttpRequestBuilder.(StepContext<*, *>, String) -> HttpRequest<*> =
            { _, _ -> request }
        val convertedResponse: QalipsisHttpResponse<String> = relaxedMockk()
        every { responseConverter.convert(response) } returns convertedResponse
        val step = spyk(
            SimpleHttpClientStep<String, String>(
                "my-step",
                null,
                requestFactory,
                connectionConfiguration,
                workerGroupSupplier,
                responseConverter,
                eventsLogger,
                meterRegistry
            )
        ) {
            coEvery { execute<String>(any(), any(), any(), any()) } returns response
        }
        val ctx =
            spyk(
                StepTestHelper.createStepContext<String, ConnectionAndRequestResult<String, QalipsisHttpResponse<String>>>(
                    input = "This is a test"
                )
            )

        // when
        step.execute(ctx)

        // then
        val monitoringCollectorCaptor = slot<StepContextBasedSocketMonitoringCollector>()
        coVerify {
            step.execute(capture(monitoringCollectorCaptor), refEq(ctx), eq("This is a test"), refEq(request))
        }
        assertThat(monitoringCollectorCaptor.captured).all {
            prop("eventsLogger").isSameAs(eventsLogger)
            prop("meterRegistry").isSameAs(meterRegistry)
            prop("stepContext").isSameAs(ctx)
            prop("eventPrefix").isEqualTo("netty.http")
            prop("meterPrefix").isEqualTo("netty-http")
        }
        val resultCaptor = slot<ConnectionAndRequestResult<String, QalipsisHttpResponse<String>>>()
        coVerify { ctx.send(capture(resultCaptor)) }
        assertThat(resultCaptor.captured).all {
            prop(RequestResult<String, QalipsisHttpResponse<String>, *>::input).isEqualTo("This is a test")
            prop(RequestResult<String, QalipsisHttpResponse<String>, *>::response).isSameAs(convertedResponse)
        }
    }

    @Test
    fun `should throws an exception when executing on a stopped step`() = testDispatcherProvider.runTest {
        // given
        val step = SimpleHttpClientStep<String, String>(
            "my-step",
            null,
            { _, _ -> SimpleHttpRequest(HttpMethod.GET, "/") },
            connectionConfiguration,
            workerGroupSupplier,
            responseConverter,
            eventsLogger,
            meterRegistry
        )

        // when
        val exception = assertThrows<IllegalArgumentException> {
            step.execute(relaxedMockk(), relaxedMockk(), "INPUT", relaxedMockk())
        }

        // then
        assertThat(exception.message).isEqualTo("The step my-step is not running")
    }

    @Test
    fun `should throws an exception and delete the client when acquiring returns an exception`() =
        testDispatcherProvider.runTest {
            val monitoringCollector = relaxedMockk<StepContextBasedSocketMonitoringCollector>()
            val ctx =
                StepTestHelper.createStepContext<String, ConnectionAndRequestResult<String, QalipsisHttpResponse<String>>>(
                    input = "This is a test",
                    minionId = "client-1"
                )
            val request = relaxedMockk<HttpRequest<*>>()
            val step = spyk(
                SimpleHttpClientStep<String, String>(
                    "my-step",
                    null,
                    { _, _ -> SimpleHttpRequest(HttpMethod.GET, "/") },
                    connectionConfiguration,
                    workerGroupSupplier,
                    responseConverter,
                    eventsLogger,
                    meterRegistry
                )
            ) {
                coEvery { createOrAcquireClient(refEq(ctx), refEq(monitoringCollector)) } throws RuntimeException()
            }
            step.setProperty("running", true)
            val client1 = relaxedMockk<MultiSocketHttpClient>()
            val client2 = relaxedMockk<MultiSocketHttpClient>()
            val client3 = relaxedMockk<MultiSocketHttpClient>()
            val client4 = relaxedMockk<MultiSocketHttpClient>()
            val clients = step.getProperty<MutableMap<MinionId, Channel<MultiSocketHttpClient>>>("clients").apply {
                put("client-1", Channel<MultiSocketHttpClient>(1).apply { trySend(client1).getOrThrow() })
                put("client-2", Channel<MultiSocketHttpClient>(1).apply { trySend(client2).getOrThrow() })
            }
            val clientsInUse = step.getProperty<MutableMap<MinionId, MultiSocketHttpClient>>("clientsInUse").apply {
                put("client-1", client3)
                put("client-2", client4)
            }

            // when
            assertThrows<RuntimeException> {
                step.execute(relaxedMockk(), ctx, "INPUT", request)
            }

            // then
            assertThat(clients).all {
                hasSize(1)
                key("client-2").isNotNull()
            }
            assertThat(clientsInUse).all {
                hasSize(1)
                key("client-2").isNotNull()
            }
        }

    @Test
    fun `should throws an exception and close the client when executing returns an exception`() =
        testDispatcherProvider.run {
            val monitoringCollector = relaxedMockk<StepContextBasedSocketMonitoringCollector>()
            val input = RandomStringUtils.randomAlphanumeric(10)
            val request = relaxedMockk<HttpRequest<*>>()
            val ctx =
                StepTestHelper.createStepContext<String, ConnectionAndRequestResult<String, QalipsisHttpResponse<String>>>(
                    input = "This is a test",
                    minionId = "client-1"
                )
            val client = relaxedMockk<MultiSocketHttpClient> {
                coEvery {
                    execute(refEq(ctx), refEq(request), refEq(monitoringCollector))
                } throws RuntimeException()
                every { isOpen } returns false
            }
            val step = spyk(
                SimpleHttpClientStep<String, String>(
                    "my-step",
                    null,
                    { _, _ -> SimpleHttpRequest(HttpMethod.GET, "/") },
                    connectionConfiguration,
                    workerGroupSupplier,
                    responseConverter,
                    eventsLogger,
                    meterRegistry
                )
            ) {
                coEvery { createOrAcquireClient(refEq(ctx), refEq(monitoringCollector)) } returns client
            }
            step.setProperty("running", true)

            // when
            assertThrows<RuntimeException> {
                step.execute(monitoringCollector, ctx, input, request)
            }

            // then
            coVerifyOnce { client.close() }
        }

    @Test
    fun `should put the client back to the maps after use`() = testDispatcherProvider.runTest {
        val monitoringCollector = relaxedMockk<StepContextBasedSocketMonitoringCollector>()
        val ctx =
            StepTestHelper.createStepContext<String, ConnectionAndRequestResult<String, QalipsisHttpResponse<String>>>(
                input = "This is a test",
                minionId = "client-1"
            )
        val input = RandomStringUtils.randomAlphanumeric(10)
        val request = relaxedMockk<HttpRequest<*>>()
        val response = relaxedMockk<HttpResponse>()
        val client = relaxedMockk<MultiSocketHttpClient> {
            coEvery {
                execute(refEq(ctx), refEq(request), refEq(monitoringCollector))
            } returns response
            every { isOpen } returns true
        }
        val step = spyk(
            SimpleHttpClientStep<String, String>(
                "my-step",
                null,
                { _, _ -> request },
                connectionConfiguration,
                workerGroupSupplier,
                responseConverter,
                eventsLogger,
                meterRegistry
            )
        ) {
            coEvery { createOrAcquireClient(refEq(ctx), refEq(monitoringCollector)) } returns client
        }
        step.setProperty("running", true)
        val clients = step.getProperty<MutableMap<MinionId, Channel<MultiSocketHttpClient>>>("clients").apply {
            put("client-1", Channel(1))
            put("client-2", Channel(1))
        }
        val clientsInUse = step.getProperty<MutableMap<MinionId, MultiSocketHttpClient>>("clientsInUse")

        // when
        val result = step.execute(monitoringCollector, ctx, input, request)

        // then
        assertThat(result).isSameAs(response)
        assertThat(clients).all {
            hasSize(2)
            key("client-1").isNotNull().transform { it.isEmpty }.isFalse()
            key("client-2").isNotNull().transform { it.isEmpty }.isTrue()
        }
        assertThat(clientsInUse).isEmpty()
    }

    @Test
    fun `should create a client if none exists`() = testDispatcherProvider.runTest {
        // given
        val monitoringCollector = relaxedMockk<StepContextBasedSocketMonitoringCollector>()
        val ctx =
            StepTestHelper.createStepContext<String, ConnectionAndRequestResult<String, QalipsisHttpResponse<String>>>(
                input = "This is a test",
                minionId = "client-1"
            )
        val client = relaxedMockk<MultiSocketHttpClient>()
        val step = spyk(
            SimpleHttpClientStep<String, String>(
                "my-step",
                null,
                { _, _ -> SimpleHttpRequest(HttpMethod.GET, "/") },
                connectionConfiguration,
                workerGroupSupplier,
                responseConverter,
                eventsLogger,
                meterRegistry
            )
        ) {
            coEvery { createClient(eq("client-1"), refEq(monitoringCollector)) } returns client
        }
        val clients = step.getProperty<MutableMap<MinionId, Channel<MultiSocketHttpClient>>>("clients").apply {
            put("client-2", Channel<MultiSocketHttpClient>(1).apply { trySend(relaxedMockk()).getOrThrow() })
        }
        val clientsInUse = step.getProperty<MutableMap<MinionId, MultiSocketHttpClient>>("clientsInUse")

        // when
        val acquiredClient = step.createOrAcquireClient(ctx, monitoringCollector)

        // then
        assertThat(acquiredClient).isSameAs(client)
        assertThat(clients).all {
            hasSize(2)
            key("client-1").isNotNull().transform { it.isEmpty }.isTrue()
            key("client-2").isNotNull().transform { it.isEmpty }.isFalse()
        }
        assertThat(clientsInUse).all {
            hasSize(1)
            key("client-1").isSameAs(client)
        }
    }

    @Test
    fun `should reuse a client if it exists`() = testDispatcherProvider.runTest {
        // given
        val monitoringCollector = relaxedMockk<StepContextBasedSocketMonitoringCollector>()
        val ctx =
            StepTestHelper.createStepContext<String, ConnectionAndRequestResult<String, QalipsisHttpResponse<String>>>(
                input = "This is a test",
                minionId = "client-1"
            )
        val client = relaxedMockk<MultiSocketHttpClient> { every { isOpen } returns true }
        val step = SimpleHttpClientStep<String, String>(
            "my-step",
            null,
            { _, _ -> SimpleHttpRequest(HttpMethod.GET, "/") },
            connectionConfiguration,
            workerGroupSupplier,
            responseConverter,
            eventsLogger,
            meterRegistry
        )
        val clients = step.getProperty<MutableMap<MinionId, Channel<MultiSocketHttpClient>>>("clients").apply {
            put("client-1", Channel<MultiSocketHttpClient>(1).apply { trySend(client).getOrThrow() })
            put("client-2", Channel<MultiSocketHttpClient>(1).apply { trySend(relaxedMockk()).getOrThrow() })
        }
        val clientsInUse = step.getProperty<MutableMap<MinionId, MultiSocketHttpClient>>("clientsInUse")

        // when
        val acquiredClient = step.createOrAcquireClient(ctx, monitoringCollector)

        // then
        assertThat(acquiredClient).isSameAs(client)
        assertThat(clients).all {
            hasSize(2)
            key("client-1").isNotNull().transform { it.isEmpty }.isTrue()
            key("client-2").isNotNull().transform { it.isEmpty }.isFalse()
        }
        assertThat(clientsInUse).all {
            hasSize(1)
            key("client-1").isSameAs(client)
        }
    }

    @Test
    fun `should throw an exception when acquiring an existing closed client`() = testDispatcherProvider.runTest {
        // given
        val monitoringCollector = relaxedMockk<StepContextBasedSocketMonitoringCollector>()
        val ctx =
            StepTestHelper.createStepContext<String, ConnectionAndRequestResult<String, QalipsisHttpResponse<String>>>(
                input = "This is a test",
                minionId = "client-1"
            )
        val client = relaxedMockk<MultiSocketHttpClient> { every { isOpen } returns false }
        val step = SimpleHttpClientStep<String, String>(
            "my-step",
            null,
            { _, _ -> SimpleHttpRequest(HttpMethod.GET, "/") },
            connectionConfiguration,
            workerGroupSupplier,
            responseConverter,
            eventsLogger,
            meterRegistry
        )
        val clients = step.getProperty<MutableMap<MinionId, Channel<MultiSocketHttpClient>>>("clients").apply {
            put("client-1", Channel<MultiSocketHttpClient>(1).apply { trySend(client).getOrThrow() })
            put("client-2", Channel<MultiSocketHttpClient>(1).apply { trySend(relaxedMockk()).getOrThrow() })
        }
        val clientsInUse = step.getProperty<MutableMap<MinionId, MultiSocketHttpClient>>("clientsInUse")

        // when
        assertThrows<ClosedClientException> {
            step.createOrAcquireClient(ctx, monitoringCollector)
        }

        // then
        assertThat(clients).all {
            hasSize(1)
            key("client-2").isNotNull().transform { it.isEmpty }.isFalse()
        }
        assertThat(clientsInUse).isEmpty()
    }
}
