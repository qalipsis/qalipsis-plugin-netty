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

package io.qalipsis.plugins.netty.udp

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.ServerUtils
import io.qalipsis.plugins.netty.configuration.ConnectionConfiguration
import io.qalipsis.plugins.netty.udp.server.UdpServer
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.PortUnreachableException
import java.nio.charset.StandardCharsets
import java.time.Duration

@WithMockk
internal class UdpClientStepIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    lateinit var meterRegistry: CampaignMeterRegistry

    @RelaxedMockK
    private lateinit var workerGroupSupplier: EventLoopGroupSupplier

    @Test
    @Timeout(TIMEOUT_SECONDS)
    internal fun `should connect to a plain UDP server and receive the echo response`() = testDispatcherProvider.run {
        val workerGroup = spyk(NativeTransportUtils.getEventLoopGroup())
        every { workerGroupSupplier.getGroup() } returns workerGroup
        val step = minimalPlainStep(this)
        step.start(relaxedMockk())
        val ctx = StepTestHelper.createStepContext<String, UdpResult<String, ByteArray>>(input = "This is a test")

        step.execute(ctx)

        val result = (ctx.output as Channel).receive().value
        step.stop(relaxedMockk())

        verifyOnce { workerGroup.shutdownGracefully() }
        assertThat(result).all {
            prop(UdpResult<String, ByteArray>::isSuccess).isTrue()
            prop(UdpResult<*, *>::isFailure).isFalse()
            prop(UdpResult<*, *>::failure).isNull()
            prop(UdpResult<*, *>::sendingFailure).isNull()
            prop(UdpResult<*, *>::cause).isNull()
            prop(UdpResult<String, ByteArray>::input).isEqualTo("This is a test")
            prop(UdpResult<String, ByteArray>::response).isNotNull()
                .transform { it.toString(StandardCharsets.UTF_8) }
                .isEqualTo("tset a si sihT")

            prop(UdpResult<String, ByteArray>::meters).all {
                prop(RequestResult.Meters::timeToFirstByte).isNotNull()
                prop(RequestResult.Meters::timeToLastByte).isNotNull()

                prop(RequestResult.Meters::bytesCountToSend).isEqualTo(14)
                prop(RequestResult.Meters::sentBytes).isEqualTo(14)
                prop(RequestResult.Meters::receivedBytes).isEqualTo(14)
            }
        }

        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

        coVerifyOrder {
            eventsLogger.debug(eq("netty.udp.sending-bytes"), eq(14), any(), any<Map<String, String>>())
            eventsLogger.debug(eq("netty.udp.sent-bytes"), any<Array<Any>>(), any(), any<Map<String, String>>())
            eventsLogger.debug(eq("netty.udp.receiving"), any<Duration>(), any(), any<Map<String, String>>())
            eventsLogger.info(eq("netty.udp.received-bytes"), any<Array<Any>>(), any(), any<Map<String, String>>())
        }
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    fun `should fail when connecting to an invalid port`() = testDispatcherProvider.run {
        val workerGroup = spyk(NativeTransportUtils.getEventLoopGroup())
        every { workerGroupSupplier.getGroup() } returns workerGroup
        val step = UdpClientStep<String>(
            "", null, { _, input -> input.toByteArray(StandardCharsets.UTF_8) },
            ConnectionConfiguration().also {
                it.address("localhost", ServerUtils.availableUdpPort())
            },
            workerGroupSupplier,
            eventsLogger, meterRegistry
        )
        step.start(relaxedMockk())
        val ctx =
            StepTestHelper.createStepContext<String, UdpResult<String, ByteArray>>(input = "This is a test")

        val result = assertThrows<UdpException> { step.execute(ctx) }.result
        step.stop(relaxedMockk())

        verifyOnce { workerGroup.shutdownGracefully() }
        assertThat(result).all {
            prop(UdpResult<*, *>::isSuccess).isFalse()
            prop(UdpResult<*, *>::isFailure).isTrue()
            prop(UdpResult<*, *>::sendingFailure).isNotNull().isInstanceOf(PortUnreachableException::class)
            prop(UdpResult<*, *>::cause).isNotNull().isInstanceOf(PortUnreachableException::class)
            prop(UdpResult<*, *>::input).isEqualTo("This is a test")
            prop(UdpResult<*, *>::response).isNull()

            prop(UdpResult<*, *>::meters).all {
                prop(RequestResult.Meters::timeToFirstByte).isNull()
                prop(RequestResult.Meters::timeToLastByte).isNull()

                prop(RequestResult.Meters::bytesCountToSend).isEqualTo(14)
                prop(RequestResult.Meters::sentBytes).isEqualTo(14)
                prop(RequestResult.Meters::receivedBytes).isEqualTo(0)
            }
        }

        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

        coVerifyOrder {
            eventsLogger.debug(eq("netty.udp.sending-bytes"), eq(14), any(), any<Map<String, String>>())
            eventsLogger.debug(eq("netty.udp.sent-bytes"), any<Array<Any>>(), any(), any<Map<String, String>>())
            eventsLogger.warn(eq("netty.udp.sending.failed"), any<Duration>(), any(), any<Map<String, String>>())
        }

        confirmVerified(eventsLogger)
    }

    private fun minimalPlainStep(ioCoroutineScope: CoroutineScope): UdpClientStep<String> {
        return UdpClientStep(
            "",
            null,
            { _, input -> input.toByteArray(StandardCharsets.UTF_8) },
            ConnectionConfiguration()
                .also { config ->
                    config.address("localhost", plainServer.port)
                },
            workerGroupSupplier,
            eventsLogger,
            meterRegistry
        )
    }

    companion object {

        const val TIMEOUT_SECONDS = 5L

        private val SERVER_HANDLER: (ByteArray) -> ByteArray = {
            it.toString(StandardCharsets.UTF_8).reversed().toByteArray(StandardCharsets.UTF_8)
        }

        @JvmField
        @RegisterExtension
        val plainServer = UdpServer.new(handler = SERVER_HANDLER)

    }
}
