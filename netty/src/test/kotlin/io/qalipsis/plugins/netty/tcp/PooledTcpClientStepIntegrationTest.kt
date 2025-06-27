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
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.setProperty
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Timer
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
import org.junit.jupiter.api.BeforeEach
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
