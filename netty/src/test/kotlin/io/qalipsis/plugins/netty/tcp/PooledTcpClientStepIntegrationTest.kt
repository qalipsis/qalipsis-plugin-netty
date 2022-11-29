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

package io.qalipsis.plugins.netty.tcp

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.setProperty
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.monitoring.StepBasedTcpMonitoringCollector
import io.qalipsis.plugins.netty.tcp.client.TcpClient
import io.qalipsis.plugins.netty.tcp.server.TcpServer
import io.qalipsis.plugins.netty.tcp.spec.TcpClientConfiguration
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyNever
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.charset.StandardCharsets
import java.time.Duration

@WithMockk
internal class PooledTcpClientStepIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var meterRegistry: CampaignMeterRegistry

    @RelaxedMockK
    private lateinit var workerGroupSupplier: EventLoopGroupSupplier

    @Test
    @Timeout(10)
    fun `should create a client`() = testDispatcherProvider.run {
        // given
        val workerGroup = spyk(NativeTransportUtils.getEventLoopGroup())
        val step = PooledTcpClientStep<String>(
            "my-step",
            null,
            this.coroutineContext,
            { _, _ -> ByteArray(0) },
            TcpClientConfiguration().apply {
                address("localhost", plainServer.port)
                readTimeout = Duration.ofMinutes(1)
                shutdownTimeout = Duration.ofSeconds(30)
            },
            relaxedMockk(),
            workerGroupSupplier,
            eventsLogger,
            meterRegistry
        ).apply {
            setProperty(
                "stepMonitoringCollector",
                StepBasedTcpMonitoringCollector(eventsLogger, meterRegistry, relaxedMockk(), "")
            )
        }

        // when
        val client = step.createClient(workerGroup)

        // then
        assertThat(client).all {
            prop("remainingUsages").isEqualTo(Long.MAX_VALUE)
            prop("readTimeout").isEqualTo(Duration.ofMinutes(1))
            prop("shutdownTimeout").isEqualTo(Duration.ofSeconds(30))
            prop(TcpClient::isOpen).isEqualTo(true)
        }
        assertThat(workerGroup).all {
            transform("isShuttingDown") { it.isShuttingDown }.isFalse()
            transform("isShuttingDown") { it.isShutdown }.isFalse()
            transform("isShuttingDown") { it.isTerminated }.isFalse()
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

        private val SERVER_HANDLER: (ByteArray) -> ByteArray = {
            "Received ${it.toString(StandardCharsets.UTF_8)}".toByteArray(StandardCharsets.UTF_8)
        }

        @JvmField
        @RegisterExtension
        val plainServer = TcpServer.new(handler = SERVER_HANDLER)

    }
}
