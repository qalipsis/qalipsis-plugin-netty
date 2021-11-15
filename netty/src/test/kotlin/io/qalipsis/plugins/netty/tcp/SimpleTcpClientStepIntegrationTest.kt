package io.qalipsis.plugins.netty.tcp

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.getProperty
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.tcp.client.TcpClient
import io.qalipsis.plugins.netty.tcp.server.TcpServer
import io.qalipsis.plugins.netty.tcp.spec.TcpClientConfiguration
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyNever
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.charset.StandardCharsets
import java.time.Duration

@WithMockk
internal class SimpleTcpClientStepIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    private lateinit var workerGroupSupplier: EventLoopGroupSupplier

    @Test
    @Timeout(10)
    fun `should create a client keeping it open`() = testDispatcherProvider.run {
        // given
        val workerGroup = spyk(NativeTransportUtils.getEventLoopGroup())
        every { workerGroupSupplier.getGroup() } returns workerGroup
        val step = SimpleTcpClientStep<String>(
            "my-step",
            null,
            this.coroutineContext,
            { _, _ -> ByteArray(0) },
            TcpClientConfiguration().apply {
                address("localhost", plainServer.port)
                readTimeout = Duration.ofMinutes(1)
                shutdownTimeout = Duration.ofSeconds(30)
                keepConnectionAlive = true
            },
            workerGroupSupplier,
            eventsLogger,
            meterRegistry
        ).apply {
            start(relaxedMockk())
        }
        val ctx =
            spyk(StepTestHelper.createStepContext<String, RequestResult<String, ByteArray, *>>(input = "This is a test"))
        val monitoring = StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test")

        // when
        val client = step.createClient("my-minion", monitoring)

        // then
        assertThat(step).all {
            typedProp<Map<MinionId, Channel<TcpClient>>>("clients").isEmpty()
            typedProp<Map<MinionId, TcpClient>>("clientsInUse").isEmpty()
        }
        assertThat(client).all {
            prop("remainingUsages").isEqualTo(Long.MAX_VALUE)
            prop("readTimeout").isEqualTo(Duration.ofMinutes(1))
            prop("shutdownTimeout").isEqualTo(Duration.ofSeconds(30))
            prop(TcpClient::isOpen).isEqualTo(true)
        }

        // given
        step.getProperty<MutableMap<MinionId, Channel<TcpClient>>>("clients")["my-minion"] =
            Channel<TcpClient>(1).apply { trySend(client).getOrThrow() }
        step.getProperty<MutableMap<MinionId, TcpClient>>("clientsInUse")["my-minion"] = client

        // when
        client.close()

        // then
        // The shared worker event loop group should not be closed when a single client is closed.
        verifyNever {
            workerGroup.shutdownGracefully()
            workerGroup.shutdown()
            workerGroup.shutdownGracefully(any(), any(), any())
        }
        // then all the maps are emptied.
        assertThat(step).all {
            typedProp<Map<MinionId, Channel<TcpClient>>>("clients").isEmpty()
            typedProp<Map<MinionId, TcpClient>>("clientsInUse").isEmpty()
        }
    }

    @Test
    @Timeout(10)
    fun `should create a client with limited usages`() = testDispatcherProvider.run {
        // given
        val workerGroup = spyk(NativeTransportUtils.getEventLoopGroup())
        every { workerGroupSupplier.getGroup() } returns workerGroup
        val step = SimpleTcpClientStep<String>(
            "my-step",
            null,
            this.coroutineContext,
            { _, _ -> ByteArray(0) },
            TcpClientConfiguration().apply {
                address("localhost", plainServer.port)
                readTimeout = Duration.ofSeconds(10)
                shutdownTimeout = Duration.ofMillis(320)
                keepConnectionAlive = false
            },
            workerGroupSupplier,
            eventsLogger,
            meterRegistry
        ).apply {
            addUsage(10)
            start(relaxedMockk())
        }
        val ctx =
            spyk(StepTestHelper.createStepContext<String, RequestResult<String, ByteArray, *>>(input = "This is a test"))
        val monitoring = StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test")

        // when
        val client = step.createClient("my-minion", monitoring)

        // then
        assertThat(step).all {
            typedProp<Map<MinionId, Channel<TcpClient>>>("clients").isEmpty()
            typedProp<Map<MinionId, TcpClient>>("clientsInUse").isEmpty()
        }
        assertThat(client).all {
            prop("remainingUsages").isEqualTo(11L)
            prop("readTimeout").isEqualTo(Duration.ofSeconds(10))
            prop("shutdownTimeout").isEqualTo(Duration.ofMillis(320))
            prop(TcpClient::isOpen).isEqualTo(true)
        }

        // given
        step.getProperty<MutableMap<MinionId, Channel<TcpClient>>>("clients")["my-minion"] =
            Channel<TcpClient>(1).apply { trySend(client).getOrThrow() }
        step.getProperty<MutableMap<MinionId, TcpClient>>("clientsInUse")["my-minion"] = client

        // when
        client.close()

        // then
        // The shared worker event loop group should not be closed when a single client is closed.
        verifyNever {
            workerGroup.shutdownGracefully()
            workerGroup.shutdown()
            workerGroup.shutdownGracefully(any(), any(), any())
        }
        // All the maps are emptied.
        assertThat(step).all {
            typedProp<Map<MinionId, Channel<TcpClient>>>("clients").isEmpty()
            typedProp<Map<MinionId, TcpClient>>("clientsInUse").isEmpty()
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
