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

package io.qalipsis.plugins.netty.http.client

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.util.ReferenceCountUtil
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Timer
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.http.client.monitoring.HttpStepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.http.request.SimpleHttpRequest
import io.qalipsis.plugins.netty.http.server.HttpServer
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.tcp.ConnectionAndRequestResult
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

@WithMockk
internal class MultiSocketHttpClientIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    lateinit var meterRegistry: CampaignMeterRegistry

    @RelaxedMockK
    private lateinit var ctx: StepContext<String, ConnectionAndRequestResult<String, String>>

    private val clientsToClean = mutableListOf<MultiSocketHttpClient>()

    private val toRelease = mutableListOf<Any>()

    private val workerGroup = NativeTransportUtils.getEventLoopGroup()

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

    @AfterAll
    internal fun finalTearDown() {
        workerGroup.shutdownGracefully()
    }

    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run {
        toRelease.forEach(ReferenceCountUtil::release)
        toRelease.clear()
        clientsToClean.forEach { kotlin.runCatching { it.close() } }
        clientsToClean.clear()
    }

    @Test
    @Timeout(10)
    internal fun `should forward when response is redirection and following is active`() = testDispatcherProvider.run {
        // given
        val monitoringCollector = HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry)
        val client = MultiSocketHttpClient(1).also(clientsToClean::add)
        client.open(
            HttpClientConfiguration().apply {
                url(forwardingHttpServer.url)
                readTimeout = Duration.ofMillis(1000)
                shutdownTimeout = Duration.ofMillis(1000)
                connectTimeout = Duration.ofMillis(1000)
                followRedirections = true
            },
            workerGroup,
            monitoringCollector
        )
        val request = SimpleHttpRequest(HttpMethod.GET, "/temporary-redirect")
        request.addParameter("location", "${plainHttpServer.url}/hello")

        // when
        val response = client.execute(ctx, request, monitoringCollector)
        toRelease.add(response)

        // then
        assertThat(response).isInstanceOf(FullHttpResponse::class).all {
            transform { it.status() }.isEqualTo(HttpResponseStatus.OK)
            transform { it.content().toString(Charsets.UTF_8) }.isEqualTo("Hello, world!")
        }
        assertThat(forwardingHttpServer.requestCount).isEqualTo(1)
        assertThat(plainHttpServer.requestCount).isEqualTo(1)
    }

    @Test
    @Timeout(10)
    internal fun `should not forward when response is redirection and following is not active`() =
        testDispatcherProvider.run {
            // given
            val monitoringCollector = HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry)
            val client = MultiSocketHttpClient(1).also(clientsToClean::add)
            client.open(
                HttpClientConfiguration().apply {
                    url(forwardingHttpServer.url)
                    readTimeout = Duration.ofMillis(1000)
                    shutdownTimeout = Duration.ofMillis(1000)
                    connectTimeout = Duration.ofMillis(1000)
                    followRedirections = false
                },
                workerGroup,
                monitoringCollector
            )
            val request = SimpleHttpRequest(HttpMethod.GET, "/temporary-redirect")
            request.addParameter("location", "${plainHttpServer.url}/get")

            // when
            val response = client.execute(ctx, request, monitoringCollector)
            toRelease.add(response)

        // then
        assertThat(response).isInstanceOf(FullHttpResponse::class).all {
            transform { it.status() }.isEqualTo(HttpResponseStatus.TEMPORARY_REDIRECT)
            transform { it.headers()[HttpHeaderNames.LOCATION] }.isEqualTo("${plainHttpServer.url}/get")
        }
        assertThat(forwardingHttpServer.requestCount).isEqualTo(1)
        assertThat(plainHttpServer.requestCount).isEqualTo(0)
    }

    @Test
    @Timeout(10)
    internal fun `should not forward when response is not redirection even if following is active`() =
        testDispatcherProvider.run {
            // given
            val monitoringCollector = HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry)
            val client = MultiSocketHttpClient(1).also(clientsToClean::add)
            client.open(
                HttpClientConfiguration().apply {
                    url(forwardingHttpServer.url)
                    readTimeout = Duration.ofMillis(1000)
                    shutdownTimeout = Duration.ofMillis(1000)
                    connectTimeout = Duration.ofMillis(1000)
                    followRedirections = true
                },
                workerGroup,
                monitoringCollector
            )
            val request = SimpleHttpRequest(HttpMethod.GET, "/hello")

            // when
            val response = client.execute(ctx, request, monitoringCollector)
            toRelease.add(response)

        // then
        assertThat(response).isInstanceOf(FullHttpResponse::class).all {
            transform { it.status() }.isEqualTo(HttpResponseStatus.OK)
            transform { it.content().toString(Charsets.UTF_8) }.isEqualTo("Hello, world!")
        }
        assertThat(forwardingHttpServer.requestCount).isEqualTo(1)
        assertThat(plainHttpServer.requestCount).isEqualTo(0)
    }

    companion object {

        @JvmField
        @RegisterExtension
        val plainHttpServer = HttpServer.new()

        @JvmField
        @RegisterExtension
        val forwardingHttpServer = HttpServer.new()

    }
}
