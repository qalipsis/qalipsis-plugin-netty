package io.qalipsis.plugins.netty.http

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
import io.netty.handler.codec.http.HttpMethod
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.http.client.MultiSocketHttpClient
import io.qalipsis.plugins.netty.http.request.SimpleHttpRequest
import io.qalipsis.plugins.netty.http.response.ResponseConverter
import io.qalipsis.plugins.netty.http.server.HttpServer
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
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
import java.time.Duration

@WithMockk
internal class SimpleHttpClientStepIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var responseConverter: ResponseConverter<String>

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
        val step = SimpleHttpClientStep<String, String>(
            "my-step",
            null,
            this,
            this.coroutineContext,
            { _, _ -> SimpleHttpRequest(HttpMethod.GET, "/") },
            HttpClientConfiguration().apply {
                address("localhost", plainServer.port)
                readTimeout = Duration.ofMinutes(1)
                shutdownTimeout = Duration.ofSeconds(30)
                keepConnectionAlive = true
            },
            workerGroupSupplier,
            responseConverter,
            eventsLogger,
            meterRegistry
        ).apply { start(relaxedMockk()) }
        val ctx =
            spyk(StepTestHelper.createStepContext<String, RequestResult<String, ByteArray, *>>(input = "This is a test"))
        val monitoring = StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test")

        // when
        val client = step.createClient("my-minion", monitoring)

        // then
        assertThat(step).all {
            typedProp<Map<MinionId, Channel<MultiSocketHttpClient>>>("clients").isEmpty()
            typedProp<Map<MinionId, MultiSocketHttpClient>>("clientsInUse").isEmpty()
        }
        assertThat(client).all {
            prop("remainingUsages").isEqualTo(Long.MAX_VALUE)
            prop(MultiSocketHttpClient::isOpen).isEqualTo(true)
        }

        // given
        step.getProperty<MutableMap<MinionId, Channel<MultiSocketHttpClient>>>("clients")["my-minion"] =
            Channel<MultiSocketHttpClient>(1).apply { trySend(client).getOrThrow() }
        step.getProperty<MutableMap<MinionId, MultiSocketHttpClient>>("clientsInUse")["my-minion"] = client

        // when
        client.close()

        // then all the maps are emptied.
        assertThat(step).all {
            typedProp<Map<MinionId, Channel<MultiSocketHttpClient>>>("clients").isEmpty()
            typedProp<Map<MinionId, MultiSocketHttpClient>>("clientsInUse").isEmpty()
        }
        // The shared worker event loop group should not be closed when a single client is closed.
        verifyNever {
            workerGroup.shutdownGracefully()
            workerGroup.shutdown()
            workerGroup.shutdownGracefully(any(), any(), any())
        }
    }

    @Test
    @Timeout(10)
    fun `should create a client with limited usages`() = testDispatcherProvider.run {
        // given
        val workerGroup = spyk(NativeTransportUtils.getEventLoopGroup())
        every { workerGroupSupplier.getGroup() } returns workerGroup
        val step = SimpleHttpClientStep<String, String>(
            "my-step",
            null,
            this,
            this.coroutineContext,
            { _, _ -> SimpleHttpRequest(HttpMethod.GET, "/") },
            HttpClientConfiguration().apply {
                address("localhost", plainServer.port)
                readTimeout = Duration.ofSeconds(10)
                shutdownTimeout = Duration.ofMillis(320)
                keepConnectionAlive = false
            },
            workerGroupSupplier,
            responseConverter,
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
            typedProp<Map<MinionId, Channel<MultiSocketHttpClient>>>("clients").isEmpty()
            typedProp<Map<MinionId, MultiSocketHttpClient>>("clientsInUse").isEmpty()
        }
        assertThat(client).all {
            prop("remainingUsages").isEqualTo(11L)
            prop(MultiSocketHttpClient::isOpen).isEqualTo(true)
        }

        // given
        step.getProperty<MutableMap<MinionId, Channel<MultiSocketHttpClient>>>("clients")["my-minion"] =
            Channel<MultiSocketHttpClient>(1).apply { trySend(client).getOrThrow() }
        step.getProperty<MutableMap<MinionId, MultiSocketHttpClient>>("clientsInUse")["my-minion"] = client

        // when
        client.close()

        // then all the maps are emptied.
        assertThat(step).all {
            typedProp<Map<MinionId, Channel<MultiSocketHttpClient>>>("clients").isEmpty()
            typedProp<Map<MinionId, MultiSocketHttpClient>>("clientsInUse").isEmpty()
        }
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
