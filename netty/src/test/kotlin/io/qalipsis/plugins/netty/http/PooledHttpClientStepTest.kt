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
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.key
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.aerisconsulting.catadioptre.getProperty
import io.aerisconsulting.catadioptre.setProperty
import io.micrometer.core.instrument.Tags
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.mockk.spyk
import io.netty.channel.EventLoopGroup
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.pool.FixedPool
import io.qalipsis.api.pool.Pool
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.http.client.HttpClient
import io.qalipsis.plugins.netty.http.request.HttpRequest
import io.qalipsis.plugins.netty.http.request.InternalHttpRequest
import io.qalipsis.plugins.netty.http.request.SimpleHttpRequest
import io.qalipsis.plugins.netty.http.response.ResponseConverter
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.monitoring.StepBasedTcpMonitoringCollector
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.SocketClient
import io.qalipsis.plugins.netty.tcp.spec.SocketClientPoolConfiguration
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyExactly
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.InetAddress
import io.qalipsis.plugins.netty.http.response.HttpResponse as QalipsisHttpResponse

@Suppress("UNCHECKED_CAST", "CHANGING_ARGUMENTS_EXECUTION_ORDER_FOR_NAMED_VARARGS")
@WithMockk
internal class PooledHttpClientStepTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var configuration: HttpClientConfiguration

    @RelaxedMockK
    private lateinit var inetAddress: InetAddress

    @SpyK
    private var poolConfiguration = SocketClientPoolConfiguration(10, true)

    @RelaxedMockK
    private lateinit var responseConverter: ResponseConverter<String>

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var meterRegistry: CampaignMeterRegistry

    @RelaxedMockK
    private lateinit var workerGroupSupplier: EventLoopGroupSupplier

    @RelaxedMockK
    private lateinit var workerGroup: EventLoopGroup

    @Test
    @Timeout(3)
    internal fun `should create the default pool at start`() = testDispatcherProvider.run {
        // given
        every { configuration.inetAddress } returns inetAddress
        every { configuration.port } returns 80
        every { workerGroupSupplier.getGroup() } returns workerGroup

        val clients = (1..10).map {
            val mock = relaxedMockk<HttpClient> {
                every { isOpen } returns true
            }
            every { mock getProperty "peerIdentifier" } returns SocketClient.RemotePeerIdentifier(inetAddress, 80)
            mock
        }
        val step = spyk(
            PooledHttpClientStep<String, String>(
                "my-step",
                null,
                this.coroutineContext,
                this,
                { _, _ -> SimpleHttpRequest(HttpMethod.GET, "/") },
                configuration,
                poolConfiguration,
                workerGroupSupplier,
                responseConverter,
                eventsLogger,
                meterRegistry
            ), recordPrivateCalls = true
        )
        coEvery { step["createClient"](any<HttpClientConfiguration>(), any<EventLoopGroup>()) } returnsMany clients

        val eventsTags = relaxedMockk<Map<String, String>>()
        val meterTags = relaxedMockk<Tags>()
        val startStopContext1 = relaxedMockk<StepStartStopContext> {
            every { toEventTags() } returns eventsTags
            every { toMetersTags() } returns meterTags
        }

        // when
        step.start(startStopContext1)

        // then
        assertThat(step).all {
            typedProp<StepBasedTcpMonitoringCollector>("stepMonitoringCollector").all {
                prop("eventsLogger").isSameAs(eventsLogger)
                prop("meterRegistry").isSameAs(meterRegistry)
                prop("eventPrefix").isEqualTo("netty.http")
                prop("metersPrefix").isEqualTo("netty-http")
                prop("eventsTags").isSameAs(eventsTags)
                prop("metersTags").isSameAs(meterTags)
            }
            typedProp<MutableMap<SocketClient.RemotePeerIdentifier, Pool<HttpClient>>>("clientsPools").all {
                hasSize(1)
                key(SocketClient.RemotePeerIdentifier(inetAddress, 80)).isInstanceOf(FixedPool::class)
            }
        }
        coVerifyOnce { workerGroupSupplier.getGroup() }
        coVerifyExactly(10) { step["createClient"](refEq(configuration), refEq(workerGroup)) }
    }

    @Test
    @Timeout(3)
    internal fun `should close all the pools at stop`() = testDispatcherProvider.run {
        // given
        val step = PooledHttpClientStep<String, String>(
            "my-step",
            null,
            this.coroutineContext,
            this,
            { _, _ -> SimpleHttpRequest(HttpMethod.GET, "/") },
            configuration,
            poolConfiguration,
            workerGroupSupplier,
            responseConverter,
            eventsLogger,
            meterRegistry
        )
        val clientsPools =
            step.getProperty<MutableMap<SocketClient.RemotePeerIdentifier, Pool<HttpClient>>>("clientsPools")
        val pools =
            listOf<Pool<HttpClient>>(relaxedMockk(), relaxedMockk(), relaxedMockk(), relaxedMockk(), relaxedMockk())
        pools.forEach { clientsPools[relaxedMockk()] = it }
        step.setProperty("workerGroup", workerGroup)

        // when
        step.stop(relaxedMockk())

        // then
        pools.forEach { coVerifyOnce { it.close() } }
        assertThat(clientsPools).isEmpty()
        verifyOnce { workerGroup.shutdownGracefully() }
    }

    @Test
    internal fun `should extract the input and execute`() = testDispatcherProvider.run {
        val request = relaxedMockk<HttpRequest<*>>()
        val requestExtractor: suspend (StepContext<*, *>, String) -> HttpRequest<*> = { _, _ -> request }
        val response = relaxedMockk<HttpResponse>()
        val convertedResponse: QalipsisHttpResponse<String> = relaxedMockk()
        every { responseConverter.convert(response) } returns convertedResponse
        val step = spyk(
            PooledHttpClientStep(
                "my-step",
                null,
                this.coroutineContext,
                this,
                requestExtractor,
                configuration,
                poolConfiguration,
                workerGroupSupplier,
                responseConverter,
                eventsLogger,
                meterRegistry
            )
        ) {
            coEvery { execute(any(), any(), eq("TEST"), refEq(request)) } returns response
        }
        val ctx =
            StepTestHelper.createStepContext<String, RequestResult<String, QalipsisHttpResponse<String>, *>>("TEST")

        // when
        step.execute(ctx)
        val result = (ctx.output as Channel<StepContext.StepOutputRecord<RequestResult<String, HttpResponse, *>>>).receive().value

        // then
        assertThat(result).all {
            prop(RequestResult<String, HttpResponse, *>::input).isEqualTo("TEST")
            prop(RequestResult<String, HttpResponse, *>::response).isSameAs(convertedResponse)
            prop(RequestResult<String, HttpResponse, *>::failure).isNull()
            prop(RequestResult<String, HttpResponse, *>::isSuccess).isTrue()
            prop(RequestResult<String, HttpResponse, *>::isFailure).isFalse()
        }
        coVerifyOnce {
            step.execute(any(), refEq(ctx), eq("TEST"), refEq(request))
        }
    }

    @Test
    @Timeout(3)
    internal fun `should return the result`() = testDispatcherProvider.run {
        val response = relaxedMockk<HttpResponse> {
            every { status() } returns HttpResponseStatus.OK
        }
        val ctx = StepTestHelper.createStepContext<String, RequestResult<String, HttpResponse, *>>("TEST")
        val request = relaxedMockk<HttpRequest<*>>()
        val requestExtractor: suspend (StepContext<*, *>, String) -> HttpRequest<*> = { _, _ -> request }
        val monitoringCollector = StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test")
        every { configuration.followRedirections } returns true
        every { configuration.maxRedirections } returns 1000

        val step = spyk(
            PooledHttpClientStep(
                "my-step",
                null,
                this.coroutineContext,
                this,
                requestExtractor,
                configuration,
                poolConfiguration,
                workerGroupSupplier,
                responseConverter,
                eventsLogger,
                meterRegistry
            ), recordPrivateCalls = true
        ) 
        coEvery { step["doExecute"](refEq(monitoringCollector), refEq(ctx), refEq(request)) } returns response
        

        // when
        val result = step.execute(monitoringCollector, ctx, "TEST", request)

        // then
        assertThat(result).isSameAs(response)
        coVerifyOnce {
            step["doExecute"](refEq(monitoringCollector), refEq(ctx), refEq(request))
        }
    }

    @Test
    @Timeout(3)
    internal fun `should not forward the request when the redirections following is disabled`() =
        testDispatcherProvider.run {
            val response = relaxedMockk<HttpResponse> {
                every { status() } returns HttpResponseStatus.PERMANENT_REDIRECT
            }
            val request = relaxedMockk<HttpRequest<*>>()

            val ctx = StepTestHelper.createStepContext<String, RequestResult<String, HttpResponse, *>>("TEST")
            val requestExtractor = relaxedMockk<suspend (StepContext<*, *>, String) -> HttpRequest<*>>()
            coEvery { requestExtractor(refEq(ctx), eq("TEST")) } returns request
            val monitoringCollector =
                StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test")
            every { configuration.followRedirections } returns false
            every { configuration.maxRedirections } returns 1000

            val step = spyk(
                PooledHttpClientStep(
                    "my-step",
                    null,
                    this.coroutineContext,
                    this,
                    requestExtractor,
                    configuration,
                    poolConfiguration,
                    workerGroupSupplier,
                    responseConverter,
                    eventsLogger,
                    meterRegistry
                ), recordPrivateCalls = true
        ) 
        coEvery { step["doExecute"](refEq(monitoringCollector), refEq(ctx), refEq(request)) } returns response
        

        // when
        val result = step.execute(monitoringCollector, ctx, "TEST", request)

        // then
        assertThat(result).isSameAs(response)
        coVerifyOnce {
            step["doExecute"](refEq(monitoringCollector), refEq(ctx), refEq(request))
        }
    }

    @Test
    @Timeout(3)
    internal fun `should only forward the request while there are redirect`() = testDispatcherProvider.run {
        val response1 = relaxedMockk<HttpResponse> {
            every { status() } returns HttpResponseStatus.PERMANENT_REDIRECT
            every { headers()[HttpHeaderNames.LOCATION] } returns "http://localhost:4500"
        }
        val response2 = relaxedMockk<HttpResponse> {
            every { status() } returns HttpResponseStatus.TEMPORARY_REDIRECT
            every { headers()[HttpHeaderNames.LOCATION] } returns "http://localhost:4500/"
        }
        val response3 = relaxedMockk<HttpResponse> {
            every { status() } returns HttpResponseStatus.OK
        }

        val request1 = relaxedMockk<HttpRequest<*>>(InternalHttpRequest::class)
        val request2 = relaxedMockk<HttpRequest<*>>(InternalHttpRequest::class)
        val request3 = relaxedMockk<HttpRequest<*>>()
        every { (request1 as InternalHttpRequest<*, *>).with(eq("http://localhost:4500")) } returns request2
        every { (request1 as InternalHttpRequest<*, *>).with(eq("http://localhost:4500/")) } returns request3

        val ctx = StepTestHelper.createStepContext<String, RequestResult<String, HttpResponse, *>>("TEST")
        val monitoringCollector = StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test")
        val requestExtractor = relaxedMockk<suspend (StepContext<*, *>, String) -> HttpRequest<*>>()

        every { configuration.followRedirections } returns true
        every { configuration.maxRedirections } returns 1000

        val step = spyk(
            PooledHttpClientStep(
                "my-step",
                null,
                this.coroutineContext,
                this,
                requestExtractor,
                configuration,
                poolConfiguration,
                workerGroupSupplier,
                responseConverter,
                eventsLogger,
                meterRegistry
            ), recordPrivateCalls = true
        ) 
            coEvery { step["doExecute"](refEq(monitoringCollector), refEq(ctx), refEq(request1)) } returns response1
            coEvery { step["doExecute"](refEq(monitoringCollector), refEq(ctx), refEq(request2)) } returns response2
            coEvery { step["doExecute"](refEq(monitoringCollector), refEq(ctx), refEq(request3)) } returns response3
        

        // when
        val result = step.execute(monitoringCollector, ctx, "TEST", request1)

        // then
        assertThat(result).isSameAs(response3)
        coVerifyOnce {
            step["doExecute"](refEq(monitoringCollector), refEq(ctx), refEq(request1))
            step["doExecute"](refEq(monitoringCollector), refEq(ctx), refEq(request2))
            step["doExecute"](refEq(monitoringCollector), refEq(ctx), refEq(request3))
        }
    }

    @Test
    @Timeout(3)
    internal fun `should only forward the request 5 times`() = testDispatcherProvider.run {
        val redirectResponse = relaxedMockk<HttpResponse> {
            every { status() } returns HttpResponseStatus.PERMANENT_REDIRECT
            every { headers()[HttpHeaderNames.LOCATION] } returns "http://localhost:4500"
        }

        val request1 = relaxedMockk<HttpRequest<*>>(InternalHttpRequest::class)
        val request2 = relaxedMockk<HttpRequest<*>>(InternalHttpRequest::class)
        every { (request1 as InternalHttpRequest<*, *>).with(any()) } returns request2

        val ctx = StepTestHelper.createStepContext<String, RequestResult<String, HttpResponse, *>>("TEST")
        val monitoringCollector = StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test")
        val requestExtractor = relaxedMockk<suspend (StepContext<*, *>, String) -> HttpRequest<*>>()
        coEvery { requestExtractor(refEq(ctx), eq("TEST")) } returns request1

        every { configuration.followRedirections } returns true
        every { configuration.maxRedirections } returns 5

        val step = spyk(
            PooledHttpClientStep(
                "my-step",
                null,
                this.coroutineContext,
                this,
                requestExtractor,
                configuration,
                poolConfiguration,
                workerGroupSupplier,
                responseConverter,
                eventsLogger,
                meterRegistry
            ), recordPrivateCalls = true
        )
        coEvery { step["doExecute"](refEq(monitoringCollector), refEq(ctx), any<HttpRequest<*>>()) } returns redirectResponse


        // when
        val result = step.execute(monitoringCollector, ctx, "TEST", request1)

        // then
        assertThat(result).isSameAs(redirectResponse)
        coVerifyOrder {
            step["doExecute"](refEq(monitoringCollector), refEq(ctx), refEq(request1))
            step["doExecute"](refEq(monitoringCollector), refEq(ctx), refEq(request2))
            step["doExecute"](refEq(monitoringCollector), refEq(ctx), refEq(request2))
            step["doExecute"](refEq(monitoringCollector), refEq(ctx), refEq(request2))
            step["doExecute"](refEq(monitoringCollector), refEq(ctx), refEq(request2))
            step["doExecute"](refEq(monitoringCollector), refEq(ctx), refEq(request2))
        }
    }

    @Test
    @Timeout(3)
    internal fun `should send the request with the existing pool attached to the request`() =
        testDispatcherProvider.run {
            // given
            val request = relaxedMockk<InternalHttpRequest<*, *>>(moreInterfaces = arrayOf(HttpRequest::class)) {
                every { computeUri(refEq(configuration)) } returns "http://localhost:80"
            }

            val response = relaxedMockk<HttpResponse>()
            val pool = relaxedMockk<Pool<HttpClient>> {
                coEvery { withPoolItem<HttpResponse>(any()) } returns response
            }
            coEvery { pool.awaitReadiness() } returns pool
            val step = PooledHttpClientStep<String, String>(
                "my-step",
                null,
                this.coroutineContext,
                this,
                relaxedMockk(),
                configuration,
                poolConfiguration,
                workerGroupSupplier,
                responseConverter,
                eventsLogger,
                meterRegistry
            )
        val clientsPools =
            step.getProperty<MutableMap<SocketClient.RemotePeerIdentifier, Pool<HttpClient>>>("clientsPools")
        clientsPools[SocketClient.RemotePeerIdentifier(InetAddress.getByName("localhost"), 80)] = pool
        val ctx = StepTestHelper.createStepContext<String, RequestResult<String, HttpResponse, *>>("TEST")
        val monitoringCollector = StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test")

        // when
        val result = step.coInvokeInvisible<HttpResponse>("doExecute", monitoringCollector, ctx, request)

        // then
        assertThat(result).isSameAs(response)
        assertThat(clientsPools).all {
            hasSize(1)
            key(SocketClient.RemotePeerIdentifier(InetAddress.getByName("localhost"), 80)).isSameAs(pool)
        }
        coVerify {
            pool.awaitReadiness()
            pool.withPoolItem<HttpResponse>(any())
        }
        confirmVerified(pool)
    }

    @Test
    @Timeout(8)
    internal fun `should send the request with a new pool attached to the request`() = testDispatcherProvider.run {
        // given
        every { workerGroupSupplier.getGroup() } returns workerGroup
        val request = relaxedMockk<InternalHttpRequest<*, *>>(moreInterfaces = arrayOf(HttpRequest::class)) {
            every { computeUri(refEq(configuration)) } returns "https://localhost:443"
        }
        val response = relaxedMockk<HttpResponse>()
        val pool1 = relaxedMockk<Pool<HttpClient>>()
        val pool2 = relaxedMockk<Pool<HttpClient>> {
            coEvery { withPoolItem<HttpResponse>(any()) } returns response
        }
        coEvery { pool2.awaitReadiness() } returns pool2
        val secondaryConfiguration = relaxedMockk<HttpClientConfiguration>()
        every { configuration.copy() } returns secondaryConfiguration
        val step = spyk(
            PooledHttpClientStep<String, String>(
                "my-step",
                null,
                this.coroutineContext,
                this,
                relaxedMockk(),
                configuration,
                poolConfiguration,
                workerGroupSupplier,
                responseConverter,
                eventsLogger,
                meterRegistry
            ), recordPrivateCalls = true
        )
        step.setProperty("stepMonitoringCollector", relaxedMockk<StepBasedTcpMonitoringCollector>())
        step.setProperty("workerGroup", workerGroup)
        val clientsPools =
            step.getProperty<MutableMap<SocketClient.RemotePeerIdentifier, Pool<HttpClient>>>("clientsPools")
        every {
            step invoke "createPool" withArguments listOf(
                refEq(secondaryConfiguration),
                refEq(workerGroup)
            )
        } returns pool2
        clientsPools[SocketClient.RemotePeerIdentifier(InetAddress.getByName("localhost"), 80)] = pool1
        val ctx = StepTestHelper.createStepContext<String, RequestResult<String, HttpResponse, *>>("TEST")
        val monitoringCollector = StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test")

        // when
        val result = step.coInvokeInvisible<HttpResponse>("doExecute", monitoringCollector, ctx, request)

        // then
        assertThat(result).isSameAs(response)
        assertThat(clientsPools).all {
            hasSize(2)
            key(SocketClient.RemotePeerIdentifier(InetAddress.getByName("localhost"), 80)).isSameAs(pool1)
            key(SocketClient.RemotePeerIdentifier(InetAddress.getByName("localhost"), 443)).isSameAs(pool2)
        }
        coVerifyOrder {
            configuration.copy()
            secondaryConfiguration.url(eq("https://localhost:443"))
            step invoke "createPool" withArguments listOf(refEq(secondaryConfiguration), refEq(workerGroup))
            pool2.awaitReadiness()
            pool2.withPoolItem<HttpResponse>(any())
        }
        confirmVerified(pool1, pool2)
    }
}
