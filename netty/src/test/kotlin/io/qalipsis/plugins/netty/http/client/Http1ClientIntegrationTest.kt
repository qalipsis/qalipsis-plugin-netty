package io.qalipsis.plugins.netty.http.client

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import com.google.common.io.Files
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.ssl.NotSslRecordException
import io.netty.util.ReferenceCountUtil
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.configuration.TlsConfiguration
import io.qalipsis.plugins.netty.http.client.monitoring.HttpStepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.http.request.FormOrMultipartHttpRequest
import io.qalipsis.plugins.netty.http.request.SimpleHttpRequest
import io.qalipsis.plugins.netty.http.server.FileMetadata
import io.qalipsis.plugins.netty.http.server.HttpServer
import io.qalipsis.plugins.netty.http.server.RequestMetadata
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.http.spec.HttpProxyType
import io.qalipsis.plugins.netty.http.spec.HttpVersion.HTTP_1_1
import io.qalipsis.plugins.netty.proxy.server.ProxyServer
import io.qalipsis.plugins.netty.tcp.ConnectionAndRequestResult
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.ConnectException
import java.nio.channels.ClosedChannelException
import java.time.Duration
import java.util.concurrent.TimeoutException
import java.util.stream.Stream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * @author Eric Jessé
 */
@WithMockk
@Timeout(30)
internal class Http1ClientIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    private lateinit var ctx: StepContext<String, ConnectionAndRequestResult<String, ByteArray>>

    private val workerGroup = NativeTransportUtils.getEventLoopGroup()

    private val toRelease = mutableListOf<Any>()

    @BeforeAll
    internal fun setUpAll() {
        httpProxyServer.addSecuredPorts(sslServerHttp.port)
    }

    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run {
        toRelease.forEach(ReferenceCountUtil::release)
        toRelease.clear()
    }

    @AfterAll
    internal fun tearDownAll() {
        workerGroup.shutdownGracefully()
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    internal fun `should connect to a server and properly close`() = testDispatcherProvider.run {
        // given
        val monitoringCollector = spyk(HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry))

        // when
        val client = HttpClient(3, this, this.coroutineContext)
        client.open(clientConfiguration(), workerGroup, monitoringCollector)

        // then
        assertThat(client.isOpen).isTrue()
        assertThat(client.isExhausted()).isFalse()
        coVerifyOrder {
            monitoringCollector.recordConnecting()
            monitoringCollector.recordConnected(more(Duration.ZERO))
            monitoringCollector.cause
        }

        // when
        client.close()

        // then
        assertThat(client.isOpen).isFalse()
        assertThat(client.isExhausted()).isFalse()
        coVerifyNever {
            monitoringCollector.recordHttpStatus(any())
        }
        val result = monitoringCollector.toResult(Unit, Unit, null);
        assertThat(result).all {
            prop(HttpITestResult::connected).isTrue()
            prop(HttpITestResult::connectionFailure).isNull()
            prop(HttpITestResult::tlsFailure).isNull()
            prop(HttpITestResult::sendingFailure).isNull()
            prop(HttpITestResult::failure).isNull()
            prop(HttpITestResult::meters).all {
                prop(HttpITMeters::timeToSuccessfulConnect).isNotNull().isGreaterThan(Duration.ZERO)
                prop(HttpITMeters::timeToFailedConnect).isNull()
                prop(HttpITMeters::timeToSuccessfulTlsConnect).isNull()
                prop(HttpITMeters::timeToFailedTlsConnect).isNull()
                prop(HttpITMeters::bytesCountToSend).isEqualTo(0)
                prop(HttpITMeters::sentBytes).isEqualTo(0)
                prop(HttpITMeters::timeToFirstByte).isNull()
                prop(HttpITMeters::timeToLastByte).isNull()
                prop(HttpITMeters::receivedBytes).isEqualTo(0)
            }
        }
    }


    @Test
    @Timeout(TIMEOUT_SECONDS)
    internal fun `should connect to a server and perform 3 requests`() = testDispatcherProvider.run {
        // given
        val monitoringCollector = spyk(HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry))

        // when
        val client = HttpClient(3, this, this.coroutineContext)
        client.open(
            HttpClientConfiguration().apply {
                version = HTTP_1_1
                url("http://localhost:${plainServerHttp.port}/status")
                readTimeout = Duration.ofMillis(100)
                shutdownTimeout = Duration.ofMillis(100)
            },
            workerGroup,
            monitoringCollector
        )

        // then
        assertThat(client.isOpen).isTrue()
        assertThat(client.isExhausted()).isFalse()
        coVerifyOrder {
            monitoringCollector.recordConnecting()
            monitoringCollector.recordConnected(more(Duration.ZERO))
            monitoringCollector.cause
        }
        var request = SimpleHttpRequest(HttpMethod.GET, "/").apply {
            addParameter("status", "BAD_REQUEST")
        }

        // when
        var result = client.execute(ctx, request, monitoringCollector)

        // then
        assertThat(result.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST)
        assertThat(client.isOpen).isTrue()
        assertThat(client.isExhausted()).isFalse()
        coVerifyOrder {
            monitoringCollector.setTags(
                "protocol" to HTTP_1_1.protocol,
                "method" to "GET",
                "scheme" to "http",
                "host" to "localhost",
                "port" to "${plainServerHttp.port}",
                "path" to "/status/"
            )
            monitoringCollector.recordSendingRequest()
        }
        coVerify(atLeast = 1) {
            monitoringCollector.recordSendingData(more(0))
            monitoringCollector.recordSentRequestSuccess()
            monitoringCollector.recordSentDataSuccess(more(0))
            monitoringCollector.recordReceivingData()
            monitoringCollector.countReceivedData(more(0))
        }
        coVerifyOrder {
            monitoringCollector.recordReceptionComplete()
            monitoringCollector.recordHttpStatus(HttpResponseStatus.BAD_REQUEST)
        }
        confirmVerified(monitoringCollector)

        // when
        request = SimpleHttpRequest(HttpMethod.POST, "/").apply {
            addParameter("status", "FORBIDDEN")
        }
        result = client.execute(ctx, request, monitoringCollector)

        // then
        assertThat(result.status()).isEqualTo(HttpResponseStatus.FORBIDDEN)
        assertThat(client.isOpen).isTrue()
        assertThat(client.isExhausted()).isFalse()
        coVerifyOrder {
            monitoringCollector.setTags(
                "protocol" to HTTP_1_1.protocol,
                "method" to "POST",
                "scheme" to "http",
                "host" to "localhost",
                "port" to "${plainServerHttp.port}",
                "path" to "/status/"
            )
            monitoringCollector.recordSendingRequest()
        }
        coVerify(atLeast = 1) {
            monitoringCollector.recordSendingData(more(0))
            monitoringCollector.recordSentRequestSuccess()
            monitoringCollector.recordSentDataSuccess(more(0))
        }
        coVerifyOrder {
            monitoringCollector.recordReceivingData()
            monitoringCollector.countReceivedData(more(0))
            monitoringCollector.recordReceptionComplete()
            monitoringCollector.recordHttpStatus(HttpResponseStatus.FORBIDDEN)
        }

        // when
        request = SimpleHttpRequest(HttpMethod.DELETE, "/").apply {
            addParameter("status", "NOT_FOUND")
        }
        result = client.execute(ctx, request, monitoringCollector)

        // then
        assertThat(result.status()).isEqualTo(HttpResponseStatus.NOT_FOUND)
        assertThat(client.isOpen).isTrue()
        assertThat(client.isExhausted()).isTrue()
        coVerifyOrder {
            monitoringCollector.setTags(
                "protocol" to HTTP_1_1.protocol,
                "method" to "DELETE",
                "scheme" to "http",
                "host" to "localhost",
                "port" to "${plainServerHttp.port}",
                "path" to "/status/"
            )
            monitoringCollector.recordSendingRequest()
        }
        coVerify(atLeast = 1) {
            monitoringCollector.recordSendingData(more(0))
            monitoringCollector.recordSentRequestSuccess()
            monitoringCollector.recordSentDataSuccess(more(0))
        }
        coVerifyOrder {
            monitoringCollector.recordReceivingData()
            monitoringCollector.countReceivedData(more(0))
            monitoringCollector.recordReceptionComplete()
            monitoringCollector.recordHttpStatus(HttpResponseStatus.NOT_FOUND)
        }
        confirmVerified(monitoringCollector)
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    internal fun `should fail when connecting to a server with invalid port`() = testDispatcherProvider.run {
        // given
        val monitoringCollector = spyk(HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry))
        val client = HttpClient(3, this, this.coroutineContext)
        val exception = assertThrows<ConnectException> {
            client.open(
                clientConfiguration().apply {
                    url("http://localhost:12133")
                    connectTimeout = Duration.ofMillis(500)
                },
                workerGroup,
                monitoringCollector
            )
        }

        // then
        assertThat(client.isOpen).isFalse()
        assertThat(client.isExhausted()).isFalse()
        coVerifyOrder {
            monitoringCollector.recordConnecting()
            monitoringCollector.recordConnectionFailure(more(Duration.ZERO), refEq(exception))
            monitoringCollector.cause
        }

        confirmVerified(monitoringCollector)
        val result = monitoringCollector.toResult(Unit, Unit, null);
        assertThat(result).all {
            prop(HttpITestResult::connected).isFalse()
            prop(HttpITestResult::connectionFailure).isSameAs(exception)
            prop(HttpITestResult::tlsFailure).isNull()
            prop(HttpITestResult::sendingFailure).isNull()
            prop(HttpITestResult::failure).isNull()
            prop(HttpITestResult::meters).all {
                prop(HttpITMeters::timeToSuccessfulConnect).isNull()
                prop(HttpITMeters::timeToFailedConnect).isNotNull().all {
                    isGreaterThan(Duration.ZERO)
                    isLessThan(Duration.ofSeconds(1))
                }
                prop(HttpITMeters::timeToSuccessfulTlsConnect).isNull()
                prop(HttpITMeters::timeToFailedTlsConnect).isNull()
                prop(HttpITMeters::bytesCountToSend).isEqualTo(0)
                prop(HttpITMeters::sentBytes).isEqualTo(0)
                prop(HttpITMeters::timeToFirstByte).isNull()
                prop(HttpITMeters::timeToLastByte).isNull()
                prop(HttpITMeters::receivedBytes).isEqualTo(0)
            }
        }
    }


    @Test
    @Timeout(TIMEOUT_SECONDS)
    internal fun `should fail when connecting to a plain server with HTTPS`() = testDispatcherProvider.run {
        // given
        val monitoringCollector = spyk(HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry))
        val client = HttpClient(3, this, this.coroutineContext)
        val exception = assertThrows<NotSslRecordException> {
            client.open(
                clientConfiguration().apply {
                    url("https://localhost:${plainServerHttp.port}")
                    connectTimeout = Duration.ofMillis(4000)
                },
                workerGroup,
                monitoringCollector
            )
        }

        // then
        assertThat(client.isOpen).isFalse()
        assertThat(client.isExhausted()).isFalse()
        coVerifyOrder {
            monitoringCollector.recordConnecting()
            monitoringCollector.recordConnected(more(Duration.ZERO))
            monitoringCollector.recordTlsHandshakeFailure(more(Duration.ZERO), refEq(exception))
            monitoringCollector.cause
        }

        confirmVerified(monitoringCollector)
        val result = monitoringCollector.toResult(Unit, Unit, null)
        assertThat(result).all {
            prop(HttpITestResult::connected).isFalse()
            prop(HttpITestResult::connectionFailure).isNull()
            prop(HttpITestResult::tlsFailure).isSameAs(exception)
            prop(HttpITestResult::sendingFailure).isNull()
            prop(HttpITestResult::failure).isNull()
            prop(HttpITestResult::meters).all {
                prop(HttpITMeters::timeToSuccessfulConnect).isNotNull().all {
                    isGreaterThan(Duration.ZERO)
                    isLessThan(Duration.ofSeconds(2))
                }
                prop(HttpITMeters::timeToFailedConnect).isNull()
                prop(HttpITMeters::timeToSuccessfulTlsConnect).isNull()
                prop(HttpITMeters::timeToFailedTlsConnect).isNotNull().all {
                    isGreaterThan(result.meters.timeToSuccessfulConnect)
                    isLessThan(Duration.ofSeconds(3))
                }
                prop(HttpITMeters::bytesCountToSend).isEqualTo(0)
                prop(HttpITMeters::sentBytes).isEqualTo(0)
                prop(HttpITMeters::timeToFirstByte).isNull()
                prop(HttpITMeters::timeToLastByte).isNull()
                prop(HttpITMeters::receivedBytes).isEqualTo(0)
            }
        }
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    internal fun `should connect to a server that dies after connection`() = testDispatcherProvider.run {
        // given
        val tempHttpServer = HttpServer.new().apply { start() }
        val monitoringCollector = spyk(HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry))
        val client = HttpClient(3, this, this.coroutineContext)
        val clientConfiguration = clientConfiguration(tempHttpServer)

        // when
        client.open(clientConfiguration, workerGroup, monitoringCollector)

        // then
        coVerifyOrder {
            monitoringCollector.recordConnecting()
            monitoringCollector.recordConnected(more(Duration.ZERO))
            monitoringCollector.cause
        }

        // when
        tempHttpServer.stop()
        delay(1000) // Wait for the server and the connection to really close.
        val request = SimpleHttpRequest(HttpMethod.GET, "/")
        val exception = assertThrows<ClosedChannelException> {
            client.execute(ctx, request, monitoringCollector)
        }

        // then
        assertThat(client.isExhausted()).isFalse()
        coVerifyOrder {
            monitoringCollector.recordSentDataFailure(any<ClosedChannelException>())
        }

        client.close()
        confirmVerified(monitoringCollector)

        val result = monitoringCollector.toResult(Unit, Unit, null);
        assertThat(result).all {
            prop(HttpITestResult::connected).isTrue()
            prop(HttpITestResult::connectionFailure).isNull()
            prop(HttpITestResult::tlsFailure).isNull()
            prop(HttpITestResult::sendingFailure).isSameAs(exception)
            prop(HttpITestResult::failure).isNull()
            prop(HttpITestResult::meters).all {
                prop(HttpITMeters::timeToSuccessfulConnect).isNotNull().all {
                    isGreaterThan(Duration.ZERO)
                    isLessThan(Duration.ofSeconds(1))
                }
                prop(HttpITMeters::timeToFailedConnect).isNull()
                prop(HttpITMeters::timeToSuccessfulTlsConnect).isNull()
                prop(HttpITMeters::timeToFailedTlsConnect).isNull()
                prop(HttpITMeters::bytesCountToSend).isEqualTo(0)
                prop(HttpITMeters::sentBytes).isEqualTo(0)
                prop(HttpITMeters::timeToFirstByte).isNull()
                prop(HttpITMeters::timeToLastByte).isNull()
                prop(HttpITMeters::receivedBytes).isEqualTo(0)
            }
        }
    }

    @Test
    @Timeout(TIMEOUT_SECONDS)
    internal fun `should throw a timeout when the server is too long`() = testDispatcherProvider.run {
        // given
        val monitoringCollector = spyk(HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry))
        val client = HttpClient(3, this, this.coroutineContext)

        // when
        client.open(
            clientConfiguration().apply {
                readTimeout = Duration.ofMillis(100)
            },
            workerGroup,
            monitoringCollector
        )

        // then
        coVerifyOrder {
            monitoringCollector.recordConnecting()
            monitoringCollector.recordConnected(more(Duration.ZERO))
            monitoringCollector.cause
        }

        // then
        assertThat(client.isOpen).isTrue()

        // when
        val request = SimpleHttpRequest(HttpMethod.GET, "/delay")
        val exception = assertThrows<TimeoutException> {
            client.execute(ctx, request, monitoringCollector)
        }

        // then
        assertThat(client.isExhausted()).isFalse()
        coVerifyOrder {
            monitoringCollector.setTags(
                "protocol" to HTTP_1_1.protocol,
                "method" to "GET",
                "scheme" to "http",
                "host" to "localhost",
                "port" to "${plainServerHttp.port}",
                "path" to "/delay"
            )
            monitoringCollector.recordSendingRequest()
            monitoringCollector.recordSendingData(more(0))
        }
        coVerifyOnce {
            monitoringCollector.recordSentRequestSuccess()
            monitoringCollector.recordSentDataSuccess(more(0))
            monitoringCollector.recordReceivingDataFailure(refEq(exception))
        }

        client.close()
        confirmVerified(monitoringCollector)

        val result = monitoringCollector.toResult(Unit, Unit, null);
        assertThat(result).all {
            prop(HttpITestResult::connected).isTrue()
            prop(HttpITestResult::connectionFailure).isNull()
            prop(HttpITestResult::tlsFailure).isNull()
            prop(HttpITestResult::sendingFailure).isSameAs(exception)
            prop(HttpITestResult::failure).isNull()
            prop(HttpITestResult::meters).all {
                prop(HttpITMeters::timeToSuccessfulConnect).isNotNull().all {
                    isGreaterThan(Duration.ZERO)
                    isLessThan(Duration.ofSeconds(1))
                }
                prop(HttpITMeters::timeToFailedConnect).isNull()
                prop(HttpITMeters::timeToSuccessfulTlsConnect).isNull()
                prop(HttpITMeters::timeToFailedTlsConnect).isNull()
                prop(HttpITMeters::bytesCountToSend).isBetween(100, 120)
                prop(HttpITMeters::sentBytes).isBetween(100, 120)
                prop(HttpITMeters::timeToFirstByte).isNull()
                prop(HttpITMeters::timeToLastByte).isNull()
                prop(HttpITMeters::receivedBytes).isEqualTo(0)
            }
        }
    }


    @ParameterizedTest(name = "should GET a response - {0}")
    @MethodSource("io.qalipsis.plugins.netty.http.client.Http1ClientIntegrationTest#allConfigurations")
    @Timeout(TIMEOUT_SECONDS)
    internal fun `should GET a response`(
        name: String, configuration: HttpClientConfiguration, server: HttpServer, proxyServer: ProxyServer?
    ) = testDispatcherProvider.run {
        val monitoringCollector = spyk(HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry))

        val request = SimpleHttpRequest(HttpMethod.GET, "/get")
        request.addParameter("param1", "value1")
        request.addParameter("param1", "value2")
        request.addParameter("param2", "value3")
        request.addHeader(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
        request.addHeader(HttpHeaderNames.COOKIE, "yummy_cookie=choco; tasty_cookie=strawberry")

        val requestMetadata = exchange(this, configuration, server, proxyServer, ctx, monitoringCollector, request)
        assertThat(requestMetadata).all {
            prop(RequestMetadata::uri).isEqualTo(
                "${server.url}/get?param1=value1&param1=value2&param2=value3"
            )
            prop(RequestMetadata::path).isEqualTo("/get")
            prop(RequestMetadata::version).isEqualTo("HTTP_1_1")
            prop(RequestMetadata::method).isEqualTo("GET")
            prop(RequestMetadata::parameters).all {
                hasSize(2)
                key("param1").all {
                    hasSize(2)
                    index(0).isEqualTo("value1")
                    index(1).isEqualTo("value2")
                }
                key("param2").all {
                    hasSize(1)
                    index(0).isEqualTo("value3")
                }
            }
            prop(RequestMetadata::headers).all {
                key(HttpHeaderNames.ACCEPT.toString()).isEqualTo(HttpHeaderValues.APPLICATION_JSON.toString())
                key(HttpHeaderNames.HOST.toString()).isEqualTo("localhost:${server.port}")
            }
            prop(RequestMetadata::cookies).all {
                hasSize(2)
                key("yummy_cookie").isEqualTo("choco")
                key("tasty_cookie").isEqualTo("strawberry")
            }
            prop(RequestMetadata::multipart).isFalse()
            prop(RequestMetadata::files).isEmpty()
            prop(RequestMetadata::size).isEqualTo(0)
            prop(RequestMetadata::data).isEqualTo("")
            prop(RequestMetadata::form).isEmpty()
        }

        coVerifyOnce {
            monitoringCollector.recordHttpStatus(HttpResponseStatus.OK)
        }
        val result = monitoringCollector.toResult(Unit, Unit, null);
        assertThat(result).all {
            prop(HttpITestResult::connected).isTrue()
            prop(HttpITestResult::connectionFailure).isNull()
            prop(HttpITestResult::tlsFailure).isNull()
            prop(HttpITestResult::sendingFailure).isNull()
            prop(HttpITestResult::failure).isNull()
            prop(HttpITestResult::meters).all {
                prop(HttpITMeters::timeToSuccessfulConnect).isNotNull().all {
                    isGreaterThan(Duration.ZERO)
                    isLessThan(Duration.ofSeconds(1))
                }
                prop(HttpITMeters::timeToFailedConnect).isNull()
                if (server.secured) {
                    prop(HttpITMeters::timeToSuccessfulTlsConnect).isNotNull().all {
                        isGreaterThan(result.meters.timeToSuccessfulConnect)
                        isLessThan(Duration.ofSeconds(2))
                    }
                } else {
                    prop(HttpITMeters::timeToSuccessfulTlsConnect).isNull()
                }
                prop(HttpITMeters::timeToFailedTlsConnect).isNull()
                prop(HttpITMeters::bytesCountToSend).isBetween(220, 240)
                prop(HttpITMeters::sentBytes).isBetween(220, 240)
                prop(HttpITMeters::timeToFirstByte).isNotNull().all {
                    isGreaterThan(result.meters.timeToSuccessfulConnect)
                    isLessThan(Duration.ofSeconds(2))
                }
                prop(HttpITMeters::timeToLastByte).isNotNull().all {
                    isGreaterThan(result.meters.timeToFirstByte)
                    isLessThan(Duration.ofSeconds(4))
                }
                prop(HttpITMeters::receivedBytes).isBetween(480, 550)
            }
        }
    }

    @ParameterizedTest(name = "should PUT data - {0}")
    @MethodSource("io.qalipsis.plugins.netty.http.client.Http1ClientIntegrationTest#allConfigurations")
    @Timeout(TIMEOUT_SECONDS)
    internal fun `should PUT data`(
        name: String, configuration: HttpClientConfiguration, server: HttpServer, proxyServer: ProxyServer?
    ) = testDispatcherProvider.run {
        val monitoringCollector = spyk(HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry))

        val request = SimpleHttpRequest(HttpMethod.PUT, "/any/url")
        request.addParameter("param1", "value1")
        request.addParameter("param1", "value2")
        request.addParameter("param2", "value3")
        request.body("This is a test", HttpHeaderValues.TEXT_PLAIN)

        val requestMetadata = exchange(this, configuration, server, proxyServer, ctx, monitoringCollector, request)
        assertThat(requestMetadata).all {
            prop(RequestMetadata::uri).isEqualTo(
                "${server.url}/any/url?param1=value1&param1=value2&param2=value3"
            )
            prop(RequestMetadata::path).isEqualTo("/any/url")
            prop(RequestMetadata::version).isEqualTo("HTTP_1_1")
            prop(RequestMetadata::method).isEqualTo("PUT")
            prop(RequestMetadata::parameters).all {
                hasSize(2)
                key("param1").all {
                    hasSize(2)
                    index(0).isEqualTo("value1")
                    index(1).isEqualTo("value2")
                }
                key("param2").all {
                    hasSize(1)
                    index(0).isEqualTo("value3")
                }
            }
            prop(RequestMetadata::headers).all {
                key(HttpHeaderNames.CONTENT_TYPE.toString()).isEqualTo("${HttpHeaderValues.TEXT_PLAIN}; charset=UTF-8")
                key(HttpHeaderNames.HOST.toString()).isEqualTo("localhost:${server.port}")
            }
            prop(RequestMetadata::cookies).isEmpty()
            prop(RequestMetadata::multipart).isFalse()
            prop(RequestMetadata::files).isEmpty()
            prop(RequestMetadata::size).isEqualTo(14)
            prop(RequestMetadata::data).isEqualTo("This is a test")
            prop(RequestMetadata::form).isEmpty()
        }


        coVerifyOnce {
            monitoringCollector.recordHttpStatus(HttpResponseStatus.OK)
        }
        val result = monitoringCollector.toResult(Unit, Unit, null);
        assertThat(result).all {
            prop(HttpITestResult::connected).isTrue()
            prop(HttpITestResult::connectionFailure).isNull()
            prop(HttpITestResult::tlsFailure).isNull()
            prop(HttpITestResult::sendingFailure).isNull()
            prop(HttpITestResult::failure).isNull()
            prop(HttpITestResult::meters).all {
                prop(HttpITMeters::timeToSuccessfulConnect).isNotNull().all {
                    isGreaterThan(Duration.ZERO)
                    isLessThan(Duration.ofSeconds(1))
                }
                prop(HttpITMeters::timeToFailedConnect).isNull()
                if (server.secured) {
                    prop(HttpITMeters::timeToSuccessfulTlsConnect).isNotNull().all {
                        isGreaterThan(result.meters.timeToSuccessfulConnect)
                        isLessThan(Duration.ofSeconds(2))
                    }
                } else {
                    prop(HttpITMeters::timeToSuccessfulTlsConnect).isNull()
                }
                prop(HttpITMeters::timeToFailedTlsConnect).isNull()
                prop(HttpITMeters::bytesCountToSend).isBetween(200, 220)
                prop(HttpITMeters::sentBytes).isBetween(200, 220)
                prop(HttpITMeters::timeToFirstByte).isNotNull().all {
                    isGreaterThan(result.meters.timeToSuccessfulConnect)
                    isLessThan(Duration.ofSeconds(2))
                }
                prop(HttpITMeters::timeToLastByte).isNotNull().all {
                    isGreaterThan(result.meters.timeToFirstByte)
                    isLessThan(Duration.ofSeconds(4))
                }
                prop(HttpITMeters::receivedBytes).isBetween(470, 535)
            }
        }
    }

    @ParameterizedTest(name = "should POST a file - {0}")
    @MethodSource("io.qalipsis.plugins.netty.http.client.Http1ClientIntegrationTest#allConfigurations")
    @Timeout(TIMEOUT_SECONDS)
    internal fun `should POST a file`(
        name: String, configuration: HttpClientConfiguration, server: HttpServer, proxyServer: ProxyServer?
    ) = testDispatcherProvider.run {
        val monitoringCollector = spyk(HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry))

        val tmpDir = Files.createTempDir()
        val file = File(tmpDir, "upload.txt")
        file.writeText("Anything in the file\r\n".repeat(1000))
        val zipArchive = File(tmpDir, "upload.txt.zip")
        val zipStream = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipArchive)))
        val zipFileEntry = ZipEntry(file.name)
        zipStream.putNextEntry(zipFileEntry)
        zipStream.write(file.readBytes())
        zipStream.closeEntry()
        zipStream.close()

        val request = FormOrMultipartHttpRequest(HttpMethod.POST, "", true)
        request.addFileUpload("uploadedFile", zipArchive, "application/x-zip-compressed", false)

        val requestMetadata = exchange(this, configuration, server, proxyServer, ctx, monitoringCollector, request)
        assertThat(requestMetadata).all {
            prop(RequestMetadata::uri).isEqualTo("${server.url}/")
            prop(RequestMetadata::path).isEqualTo("/")
            prop(RequestMetadata::version).isEqualTo("HTTP_1_1")
            prop(RequestMetadata::method).isEqualTo("POST")
            prop(RequestMetadata::parameters).isEmpty()
            prop(RequestMetadata::headers).all {
                key(HttpHeaderNames.HOST.toString()).isEqualTo("localhost:${server.port}")
                key(HttpHeaderNames.CONNECTION.toString()).isEqualTo(HttpHeaderValues.KEEP_ALIVE.toString())
                key(HttpHeaderNames.CONTENT_TYPE.toString()).startsWith("${HttpHeaderValues.MULTIPART_FORM_DATA}; boundary=")
            }
            prop(RequestMetadata::cookies).isEmpty()
            prop(RequestMetadata::multipart).isTrue()
            prop(RequestMetadata::files).all {
                hasSize(1)
                key("uploadedFile").all {
                    hasSize(1)
                    index(0).all {
                        prop(FileMetadata::name).isEqualTo("upload.txt.zip")
                        prop(FileMetadata::size).isEqualTo(230)
                        prop(FileMetadata::contentType).isEqualTo("application/x-zip-compressed")
                    }
                }
            }
            prop(RequestMetadata::size).isBetween(450, 460)
            prop(RequestMetadata::data).isNull()
            prop(RequestMetadata::form).isEmpty()
        }

        coVerifyOnce {
            monitoringCollector.recordHttpStatus(HttpResponseStatus.OK)
        }
        val result = monitoringCollector.toResult(Unit, Unit, null);
        assertThat(result).all {
            prop(HttpITestResult::connected).isTrue()
            prop(HttpITestResult::connectionFailure).isNull()
            prop(HttpITestResult::tlsFailure).isNull()
            prop(HttpITestResult::sendingFailure).isNull()
            prop(HttpITestResult::failure).isNull()
            prop(HttpITestResult::meters).all {
                prop(HttpITMeters::timeToSuccessfulConnect).isNotNull().all {
                    isGreaterThan(Duration.ZERO)
                    isLessThan(Duration.ofSeconds(1))
                }
                prop(HttpITMeters::timeToFailedConnect).isNull()
                if (server.secured) {
                    prop(HttpITMeters::timeToSuccessfulTlsConnect).isNotNull().all {
                        isGreaterThan(result.meters.timeToSuccessfulConnect)
                        isLessThan(Duration.ofSeconds(2))
                    }
                } else {
                    prop(HttpITMeters::timeToSuccessfulTlsConnect).isNull()
                }
                prop(HttpITMeters::timeToFailedTlsConnect).isNull()
                prop(HttpITMeters::bytesCountToSend).isBetween(640, 680)
                prop(HttpITMeters::sentBytes).isBetween(640, 680)
                prop(HttpITMeters::timeToFirstByte).isNotNull().all {
                    isGreaterThan(result.meters.timeToSuccessfulConnect)
                    isLessThan(Duration.ofSeconds(2))
                }
                prop(HttpITMeters::timeToLastByte).isNotNull().all {
                    isGreaterThan(result.meters.timeToFirstByte)
                    isLessThan(Duration.ofSeconds(4))
                }
                prop(HttpITMeters::receivedBytes).isBetween(480, 550)
            }
        }
    }


    @ParameterizedTest(name = "should PATCH a chunked request with a form - {0}")
    @MethodSource("io.qalipsis.plugins.netty.http.client.Http1ClientIntegrationTest#allConfigurations")
    @Timeout(TIMEOUT_SECONDS)
    internal fun `should PATCH a chunked request with a form`(
        name: String, configuration: HttpClientConfiguration, server: HttpServer, proxyServer: ProxyServer?
    ) = testDispatcherProvider.run {
        val monitoringCollector = spyk(HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry))

        val request = FormOrMultipartHttpRequest(HttpMethod.PATCH, "/send/form")
        request.addHeader(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
        request.addAttribute("info", "first value")
        request.addAttribute("secondinfo", "secondvalue ���&")
        request.addAttribute("thirdinfo", "Short text")
        request.addAttribute("fourthinfo", ("M".repeat(98) + "\r\n").repeat(100))

        val requestMetadata = exchange(this, configuration, server, proxyServer, ctx, monitoringCollector, request)

        assertThat(requestMetadata).all {
            prop(RequestMetadata::uri).isEqualTo("${server.url}/send/form")
            prop(RequestMetadata::path).isEqualTo("/send/form")
            prop(RequestMetadata::version).isEqualTo("HTTP_1_1")
            prop(RequestMetadata::method).isEqualTo("PATCH")
            prop(RequestMetadata::parameters).isEmpty()
            prop(RequestMetadata::headers).all {
                key(HttpHeaderNames.ACCEPT.toString()).isEqualTo(HttpHeaderValues.APPLICATION_JSON.toString())
                key(HttpHeaderNames.HOST.toString()).isEqualTo("localhost:${server.port}")
                key(HttpHeaderNames.CONNECTION.toString()).isEqualTo(HttpHeaderValues.KEEP_ALIVE.toString())
                key(HttpHeaderNames.CONTENT_TYPE.toString()).isEqualTo(
                    HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString()
                )
            }
            prop(RequestMetadata::cookies).isEmpty()
            prop(RequestMetadata::multipart).isFalse()
            prop(RequestMetadata::files).isEmpty()
            prop(RequestMetadata::size).isEqualTo(10503)
            prop(RequestMetadata::data).isNull()
            prop(RequestMetadata::form).all {
                hasSize(4)
                key("info").all {
                    hasSize(1)
                    index(0).isEqualTo("first value")
                }
                key("secondinfo").all {
                    hasSize(1)
                    index(0).isEqualTo("secondvalue ���&")
                }
                key("thirdinfo").all {
                    hasSize(1)
                    index(0).isEqualTo("Short text")
                }
                key("fourthinfo").all {
                    hasSize(1)
                    index(0).isEqualTo(("M".repeat(98) + "\r\n").repeat(100))
                }
            }
        }

        coVerifyOnce {
            monitoringCollector.recordHttpStatus(HttpResponseStatus.OK)
        }
        val result = monitoringCollector.toResult(Unit, Unit, null);
        assertThat(result).all {
            prop(HttpITestResult::connected).isTrue()
            prop(HttpITestResult::connectionFailure).isNull()
            prop(HttpITestResult::tlsFailure).isNull()
            prop(HttpITestResult::sendingFailure).isNull()
            prop(HttpITestResult::failure).isNull()
            prop(HttpITestResult::meters).all {
                prop(HttpITMeters::timeToSuccessfulConnect).isNotNull().all {
                    isGreaterThan(Duration.ZERO)
                    isLessThan(Duration.ofSeconds(1))
                }
                prop(HttpITMeters::timeToFailedConnect).isNull()
                if (server.secured) {
                    prop(HttpITMeters::timeToSuccessfulTlsConnect).isNotNull().all {
                        isGreaterThan(result.meters.timeToSuccessfulConnect)
                        isLessThan(Duration.ofSeconds(2))
                    }
                } else {
                    prop(HttpITMeters::timeToSuccessfulTlsConnect).isNull()
                }
                prop(HttpITMeters::timeToFailedTlsConnect).isNull()
                prop(HttpITMeters::bytesCountToSend).isBetween(10620, 10760)
                prop(HttpITMeters::sentBytes).isBetween(10620, 10760)
                prop(HttpITMeters::timeToFirstByte).isNotNull().all {
                    isGreaterThan(result.meters.timeToSuccessfulConnect)
                    isLessThan(Duration.ofSeconds(2))
                }
                prop(HttpITMeters::timeToLastByte).isNotNull().all {
                    isGreaterThan(result.meters.timeToFirstByte)
                    isLessThan(Duration.ofSeconds(4))
                }
                prop(HttpITMeters::receivedBytes).isBetween(10730, 10800)
            }
        }
    }

    private suspend fun exchange(
        coroutineScope: CoroutineScope,
        configuration: HttpClientConfiguration,
        server: HttpServer,
        proxyServer: ProxyServer?,
        stepContext: StepContext<*, *>,
        monitoringCollector: HttpStepContextBasedSocketMonitoringCollector,
        request: io.qalipsis.plugins.netty.http.request.HttpRequest<*>
    ): RequestMetadata {
        val client = HttpClient(2, coroutineScope, coroutineScope.coroutineContext)
        val result = try {
            client.open(configuration, workerGroup, monitoringCollector)
            client.execute(stepContext, request, monitoringCollector)
        } catch (e: Exception) {
            log.error { e.message }
            client.close()
            throw e
        }

        return try {
            Http2ClientIntegrationTest.log.trace { "Closing the temporary test HTTP client" }
            assertThat(client.isOpen).isTrue()
            client.close()

            proxyServer?.let {
                assertThat(proxyServer.requestCount).isGreaterThanOrEqualTo(1)
            }

            coVerifyOrder {
                monitoringCollector.recordConnecting()
                monitoringCollector.recordConnected(more(Duration.ZERO))
                if (server.secured) {
                    monitoringCollector.recordTlsHandshakeSuccess(more(Duration.ZERO))
                }
                monitoringCollector.cause

                monitoringCollector.setTags(
                    "protocol" to configuration.version.protocol,
                    match { it.first == "method" && it.second in setOf("GET", "POST", "PUT", "PATCH", "DELETE") },
                    "scheme" to if (server.secured) "https" else "http",
                    "host" to "localhost",
                    "port" to "${server.port}",
                    match { it.first == "path" && it.second.isNotEmpty() }
                )
            }
            RequestMetadata.parse((result as HttpContent).content())
        } finally {
            (result as HttpContent).content().release()
        }
    }

    companion object {

        init {
            System.setProperty("QALIPSIS_LOGGING_LEVEL", "trace")
        }

        const val TIMEOUT_SECONDS = 10L

        @JvmStatic
        fun allConfigurations(): Stream<Arguments> = Stream.of(
            // HTTP.
            Arguments.of("HTTP 1.1", clientConfiguration(), plainServerHttp, null),

            // HTTPS.
            Arguments.of("HTTPS 1.1", sslClientConfiguration(), sslServerHttp, null),

            // HTTP via a HTTP proxy.
            Arguments.of("HTTP 1.1 over HTTP proxy", clientConfiguration().apply {
                proxy { address("localhost", httpProxyServer.port) }
            }, plainServerHttp, httpProxyServer),

            // HTTP via a Socks5 proxy.
            Arguments.of("HTTP 1.1 over Socks5 proxy", clientConfiguration().apply {
                proxy {
                    address("localhost", sock5ProxyServer.port)
                    type = HttpProxyType.SOCKS5
                }
            }, plainServerHttp, sock5ProxyServer),

            // HTTPS via a Socks5 proxy.
            Arguments.of("HTTPS 1.1 over Socks5 proxy", sslClientConfiguration().apply {
                proxy {
                    address("localhost", sock5ProxyServer.port)
                    type = HttpProxyType.SOCKS5
                }
            }, sslServerHttp, sock5ProxyServer)

        )

        @JvmStatic
        private fun sslClientConfiguration() = clientConfiguration(sslServerHttp)

        @JvmStatic
        private fun clientConfiguration(server: HttpServer = plainServerHttp): HttpClientConfiguration {
            return HttpClientConfiguration(
                tlsConfiguration = TlsConfiguration(
                    disableCertificateVerification = true
                )
            ).apply {
                url(server.url)
                connectTimeout = Duration.ofSeconds(3)
                readTimeout = Duration.ofSeconds(2)
                shutdownTimeout = Duration.ofSeconds(3)
            }
        }

        @JvmField
        @RegisterExtension
        val plainServerHttp = HttpServer.new()

        @JvmField
        @RegisterExtension
        val sslServerHttp = HttpServer.new(enableTls = true)

        @JvmField
        @RegisterExtension
        val httpProxyServer = ProxyServer.newHttp()

        @JvmField
        @RegisterExtension
        val sock5ProxyServer = ProxyServer.newSocks()

        @JvmStatic
        private val log = logger()


    }

}
