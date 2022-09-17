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
import assertk.assertions.*
import io.aerisconsulting.catadioptre.getProperty
import io.aerisconsulting.catadioptre.setProperty
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import io.netty.channel.EventLoopGroup
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.exceptions.ClosedClientException
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.tcp.client.TcpClient
import io.qalipsis.plugins.netty.tcp.spec.TcpClientConfiguration
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class SimpleTcpClientStepTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var connectionConfiguration: TcpClientConfiguration

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    private lateinit var workerGroupSupplier: EventLoopGroupSupplier

    @RelaxedMockK
    private lateinit var workerGroup: EventLoopGroup

    @Test
    fun `should change the connection configuration to keep open`() = testDispatcherProvider.runTest {
        // given
        val config = TcpClientConfiguration().apply { keepConnectionAlive = false }
        val step = SimpleTcpClientStep<String>(
            "my-step",
            null,
            this.coroutineContext,
            { _, _ -> ByteArray(0) },
            config,
            workerGroupSupplier,
            eventsLogger,
            meterRegistry
        )

        // when
        step.keepOpen()

        // then
        assertThat(config.keepConnectionAlive).isTrue()
    }

    @Test
    fun `should make the step running after start`() = testDispatcherProvider.run {
        // given
        every { workerGroupSupplier.getGroup() } returns workerGroup
        val step = SimpleTcpClientStep<String>(
            "my-step",
            null,
            this.coroutineContext,
            { _, _ -> ByteArray(0) },
            connectionConfiguration,
            workerGroupSupplier,
            eventsLogger,
            meterRegistry
        )

        // when
        step.start(relaxedMockk())

        // then
        assertThat(step).all {
            prop("running").isEqualTo(true)
            prop("workerGroup").isSameAs(workerGroup)
        }
    }

    @Test
    fun `should close all the clients at stop`() = testDispatcherProvider.run {
        // given
        val step = SimpleTcpClientStep<String>(
            "my-step",
            null,
            this.coroutineContext,
            { _, _ -> ByteArray(0) },
            connectionConfiguration,
            workerGroupSupplier,
            eventsLogger,
            meterRegistry
        )
        step.setProperty("running", true)
        step.setProperty("workerGroup", workerGroup)
        val client1 = relaxedMockk<TcpClient>()
        val client2 = relaxedMockk<TcpClient>()
        val client3 = relaxedMockk<TcpClient>()
        val client4 = relaxedMockk<TcpClient>()
        step.getProperty<MutableMap<MinionId, Channel<TcpClient>>>("clients").apply {
            put("1", Channel<TcpClient>(1).apply { trySend(client1).getOrThrow() })
            put("2", Channel<TcpClient>(1).apply { trySend(client2).getOrThrow() })
        }
        step.getProperty<MutableMap<MinionId, TcpClient>>("clientsInUse").apply {
            put("1", client3)
            put("2", client4)
        }

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
            typedProp<Map<MinionId, Channel<TcpClient>>>("clients").isEmpty()
            typedProp<Map<MinionId, TcpClient>>("clientsInUse").isEmpty()
        }
    }

    @Test
    fun `should execute the request from the step context`() = testDispatcherProvider.run {
        // given
        val request = ByteArray(0)
        val response = ByteArray(0)
        val requestFactory: suspend (StepContext<*, *>, String) -> ByteArray = { _, _ -> request }
        val step = spyk(
            SimpleTcpClientStep(
                "my-step",
                null,
                this.coroutineContext,
                requestFactory,
                connectionConfiguration,
                workerGroupSupplier,
                eventsLogger,
                meterRegistry
            )
        ) {
            coEvery { execute<String>(any(), any(), any(), any()) } returns response
        }
        val ctx =
            spyk(StepTestHelper.createStepContext<String, ConnectionAndRequestResult<String, ByteArray>>(input = "This is a test"))

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
            prop("eventPrefix").isEqualTo("netty.tcp")
            prop("metersPrefix").isEqualTo("netty-tcp")
        }
        val resultCaptor = slot<ConnectionAndRequestResult<String, ByteArray>>()
        coVerify { ctx.send(capture(resultCaptor)) }
        assertThat(resultCaptor.captured).all {
            prop(RequestResult<String, ByteArray, *>::input).isEqualTo("This is a test")
            prop(RequestResult<String, ByteArray, *>::response).isSameAs(response)
        }
    }

    @Test
    fun `should throws an exception when executing on a stopped step`() = testDispatcherProvider.run {
        // given
        val step = SimpleTcpClientStep<String>(
            "my-step",
            null,
            this.coroutineContext,
            { _, _ -> ByteArray(0) },
            connectionConfiguration,
            workerGroupSupplier,
            eventsLogger,
            meterRegistry
        )

        // when
        val exception = assertThrows<IllegalArgumentException> {
            step.execute(relaxedMockk(), relaxedMockk(), "INPUT", ByteArray(0))
        }

        // then
        assertThat(exception.message).isEqualTo("The step my-step is not running")
    }

    @Test
    fun `should throws an exception and delete the client when acquiring returns an exception`() =
        testDispatcherProvider.run {
            val request = ByteArray(0)
            val monitoringCollector = relaxedMockk<StepContextBasedSocketMonitoringCollector>()
            val ctx = StepTestHelper.createStepContext<String, ConnectionAndRequestResult<String, ByteArray>>(
                input = "This is a test",
                minionId = "client-1"
            )
            val step = spyk(
                SimpleTcpClientStep<String>(
                    "my-step",
                    null,
                    this.coroutineContext,
                    { _, _ -> ByteArray(0) },
                    connectionConfiguration,
                    workerGroupSupplier,
                    eventsLogger,
                    meterRegistry
                )
            ) {
                coEvery { createOrAcquireClient(refEq(monitoringCollector), refEq(ctx)) } throws RuntimeException()
            }
            step.setProperty("running", true)
            val client1 = relaxedMockk<TcpClient>()
            val client2 = relaxedMockk<TcpClient>()
            val client3 = relaxedMockk<TcpClient>()
            val client4 = relaxedMockk<TcpClient>()
            val clients = step.getProperty<MutableMap<MinionId, Channel<TcpClient>>>("clients").apply {
                put("client-1", Channel<TcpClient>(1).apply { trySend(client1).getOrThrow() })
                put("client-2", Channel<TcpClient>(1).apply { trySend(client2).getOrThrow() })
            }
            val clientsInUse = step.getProperty<MutableMap<MinionId, TcpClient>>("clientsInUse").apply {
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
    fun `should throws an exception and close the client when executing returns an exception`() = testDispatcherProvider.run {
        val request = ByteArray(0)
        val monitoringCollector = relaxedMockk<StepContextBasedSocketMonitoringCollector>()
        val input = RandomStringUtils.randomAlphanumeric(10)
        val ctx = StepTestHelper.createStepContext<String, ConnectionAndRequestResult<String, ByteArray>>(
            input = "This is a test",
            minionId = "client-1"
        )
        val client = relaxedMockk<TcpClient> {
            coEvery {
                execute(refEq(ctx), eq(request), refEq(monitoringCollector))
            } throws RuntimeException()
            every { isOpen } returns false
        }
        val step = spyk(
            SimpleTcpClientStep<String>(
                "my-step",
                null,
                this.coroutineContext,
                { _, _ -> ByteArray(0) },
                connectionConfiguration,
                workerGroupSupplier,
                eventsLogger,
                meterRegistry
            )
        ) {
            coEvery { createOrAcquireClient(refEq(monitoringCollector), refEq(ctx)) } returns client
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
    fun `should put the client back to the maps after use`() = testDispatcherProvider.run {
        val request = ByteArray(0)
        val monitoringCollector = relaxedMockk<StepContextBasedSocketMonitoringCollector>()
        val ctx = StepTestHelper.createStepContext<String, ConnectionAndRequestResult<String, ByteArray>>(
            input = "This is a test",
            minionId = "client-1"
        )
        val input = RandomStringUtils.randomAlphanumeric(10)
        val response = input.reversed().toByteArray()
        val client = relaxedMockk<TcpClient> {
            coEvery {
                execute(refEq(ctx), eq(request), refEq(monitoringCollector))
            } returns response
            every { isOpen } returns true
        }
        val step = spyk(
            SimpleTcpClientStep<String>(
                "my-step",
                null,
                this.coroutineContext,
                { _, _ -> ByteArray(0) },
                connectionConfiguration,
                workerGroupSupplier,
                eventsLogger,
                meterRegistry
            )
        ) {
            coEvery { createOrAcquireClient(refEq(monitoringCollector), refEq(ctx)) } returns client
        }
        step.setProperty("running", true)
        val clients = step.getProperty<MutableMap<MinionId, Channel<TcpClient>>>("clients").apply {
            put("client-1", Channel(1))
            put("client-2", Channel(1))
        }
        val clientsInUse = step.getProperty<MutableMap<MinionId, TcpClient>>("clientsInUse")

        // when
        val result = step.execute(monitoringCollector, ctx, input, request)

        // then
        assertThat(result).isEqualTo(response)
        assertThat(clients).all {
            hasSize(2)
            key("client-1").isNotNull().transform { it.isEmpty }.isFalse()
            key("client-2").isNotNull().transform { it.isEmpty }.isTrue()
        }
        assertThat(clientsInUse).isEmpty()
    }

    @Test
    fun `should create a client if none exists`() = testDispatcherProvider.run {
        // given
        val monitoringCollector = relaxedMockk<StepContextBasedSocketMonitoringCollector>()
        val ctx = StepTestHelper.createStepContext<String, ConnectionAndRequestResult<String, ByteArray>>(
            input = "This is a test",
            minionId = "client-1"
        )
        val client = relaxedMockk<TcpClient>()
        val step = spyk(
            SimpleTcpClientStep<String>(
                "my-step",
                null,
                this.coroutineContext,
                { _, _ -> ByteArray(0) },
                connectionConfiguration,
                workerGroupSupplier,
                eventsLogger,
                meterRegistry
            )
        ) {
            coEvery { createClient(eq("client-1"), refEq(monitoringCollector)) } returns client
        }
        val clients = step.getProperty<MutableMap<MinionId, Channel<TcpClient>>>("clients").apply {
            put("client-2", Channel<TcpClient>(1).apply { trySend(relaxedMockk()).getOrThrow() })
        }
        val clientsInUse = step.getProperty<MutableMap<MinionId, TcpClient>>("clientsInUse")

        // when
        val acquiredClient = step.createOrAcquireClient(monitoringCollector, ctx)

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
    fun `should reuse a client if it exists`() = testDispatcherProvider.run {
        // given
        val monitoringCollector = relaxedMockk<StepContextBasedSocketMonitoringCollector>()
        val ctx = StepTestHelper.createStepContext<String, ConnectionAndRequestResult<String, ByteArray>>(
            input = "This is a test",
            minionId = "client-1"
        )
        val client = relaxedMockk<TcpClient> { every { isOpen } returns true }
        val step = SimpleTcpClientStep<String>(
            "my-step",
            null,
            this.coroutineContext,
            { _, _ -> ByteArray(0) },
            connectionConfiguration,
            workerGroupSupplier,
            eventsLogger,
            meterRegistry
        )
        val clients = step.getProperty<MutableMap<MinionId, Channel<TcpClient>>>("clients").apply {
            put("client-1", Channel<TcpClient>(1).apply { trySend(client).getOrThrow() })
            put("client-2", Channel<TcpClient>(1).apply { trySend(relaxedMockk()).getOrThrow() })
        }
        val clientsInUse = step.getProperty<MutableMap<MinionId, TcpClient>>("clientsInUse")

        // when
        val acquiredClient = step.createOrAcquireClient(monitoringCollector, ctx)

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
    fun `should throw an exception when acquiring an existing closed client`() = testDispatcherProvider.run {
        // given
        val monitoringCollector = relaxedMockk<StepContextBasedSocketMonitoringCollector>()
        val ctx = StepTestHelper.createStepContext<String, ConnectionAndRequestResult<String, ByteArray>>(
            input = "This is a test",
            minionId = "client-1"
        )
        val client = relaxedMockk<TcpClient> { every { isOpen } returns false }
        val step = SimpleTcpClientStep<String>(
            "my-step",
            null,
            this.coroutineContext,
            { _, _ -> ByteArray(0) },
            connectionConfiguration,
            workerGroupSupplier,
            eventsLogger,
            meterRegistry
        )
        val clients = step.getProperty<MutableMap<MinionId, Channel<TcpClient>>>("clients").apply {
            put("client-1", Channel<TcpClient>(1).apply { trySend(client).getOrThrow() })
            put("client-2", Channel<TcpClient>(1).apply { trySend(relaxedMockk()).getOrThrow() })
        }
        val clientsInUse = step.getProperty<MutableMap<MinionId, TcpClient>>("clientsInUse")

        // when
        assertThrows<ClosedClientException> {
            step.createOrAcquireClient(monitoringCollector, ctx)
        }

        // then
        assertThat(clients).all {
            hasSize(1)
            key("client-2").isNotNull().transform { it.isEmpty }.isFalse()
        }
        assertThat(clientsInUse).isEmpty()
    }
}
