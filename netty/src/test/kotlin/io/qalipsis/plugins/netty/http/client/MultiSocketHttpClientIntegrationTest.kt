package io.qalipsis.plugins.netty.http.client

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.impl.annotations.RelaxedMockK
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.util.ReferenceCountUtil
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.http.client.monitoring.HttpStepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.http.request.SimpleHttpRequest
import io.qalipsis.plugins.netty.http.server.HttpServer
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.tcp.ConnectionAndRequestResult
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
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
    lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    private lateinit var ctx: StepContext<String, ConnectionAndRequestResult<String, String>>

    private val clientsToClean = mutableListOf<MultiSocketHttpClient>()

    private val toRelease = mutableListOf<Any>()

    private val workerGroup = NativeTransportUtils.getEventLoopGroup()

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
        val client = MultiSocketHttpClient(1, this, this.coroutineContext).also(clientsToClean::add)
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
            val client = MultiSocketHttpClient(1, this, this.coroutineContext).also(clientsToClean::add)
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
            val client = MultiSocketHttpClient(1, this, this.coroutineContext).also(clientsToClean::add)
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