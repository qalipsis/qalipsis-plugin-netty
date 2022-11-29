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
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.aerisconsulting.catadioptre.setProperty
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.netty.handler.codec.http.HttpMethod
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.http.client.HttpClient
import io.qalipsis.plugins.netty.http.request.SimpleHttpRequest
import io.qalipsis.plugins.netty.http.response.ResponseConverter
import io.qalipsis.plugins.netty.http.server.HttpServer
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.monitoring.StepBasedTcpMonitoringCollector
import io.qalipsis.plugins.netty.tcp.spec.SocketClientPoolConfiguration
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyNever
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

@WithMockk
internal class PooledHttpClientStepIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var responseConverter: ResponseConverter<String>

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var meterRegistry: CampaignMeterRegistry

    @RelaxedMockK
    private lateinit var workerGroupSupplier: EventLoopGroupSupplier

    @Test
    @Timeout(5)
    internal fun `should create the client with the provided configuration`() = testDispatcherProvider.run {
        // given
        val workerGroup = spyk(NativeTransportUtils.getEventLoopGroup())
        every { workerGroupSupplier.getGroup() } returns workerGroup
        val step = PooledHttpClientStep<String, String>(
            "my-step",
            null,
            this.coroutineContext,
            this,
            { _, _ -> SimpleHttpRequest(HttpMethod.GET, "/") },
            HttpClientConfiguration().apply {
                url("http://localhost:1")
                readTimeout = Duration.ofMillis(100)
                shutdownTimeout = Duration.ofMillis(100)
            },
            SocketClientPoolConfiguration(2, true),
            workerGroupSupplier,
            responseConverter,
            eventsLogger,
            meterRegistry
        ).apply {
            setProperty(
                "stepMonitoringCollector",
                StepBasedTcpMonitoringCollector(eventsLogger, meterRegistry, relaxedMockk(), "")
            )
        }

        // when
        val client = step.coInvokeInvisible<HttpClient>("createClient", HttpClientConfiguration().apply {
            url("http://localhost:${plainServer.port}")
            readTimeout = Duration.ofMinutes(1)
            shutdownTimeout = Duration.ofSeconds(30)
        }, workerGroup)

        // then
        assertThat(client).all {
            prop("remainingUsages").isEqualTo(Long.MAX_VALUE)
            prop("readTimeout").isEqualTo(Duration.ofMinutes(1))
            prop("shutdownTimeout").isEqualTo(Duration.ofSeconds(30))
            prop(HttpClient::isOpen).isEqualTo(true)
        }

        // when
        client.close()

        // then
        // The shared worker event loop group should not be closed when a single client is closed.
        verifyNever {
            workerGroup.shutdownGracefully()
            workerGroup.shutdown()
            workerGroup.shutdownGracefully(any(), any(), any())
        }
    }

    companion object {

        @JvmField
        @RegisterExtension
        val plainServer = HttpServer.new()

    }
}
