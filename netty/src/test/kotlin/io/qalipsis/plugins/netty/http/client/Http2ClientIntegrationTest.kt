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
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isBetween
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.key
import assertk.assertions.prop
import assertk.assertions.startsWith
import com.google.common.io.Files
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
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
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Timer
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.configuration.TlsConfiguration
import io.qalipsis.plugins.netty.http.client.monitoring.HttpStepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.http.request.FormOrMultipartHttpRequest
import io.qalipsis.plugins.netty.http.request.SimpleHttpRequest
import io.qalipsis.plugins.netty.http.server.FileMetadata
import io.qalipsis.plugins.netty.http.server.HttpServer
import io.qalipsis.plugins.netty.http.server.RequestMetadata
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.http.spec.HttpVersion.HTTP_2_0
import io.qalipsis.plugins.netty.tcp.ConnectionAndRequestResult
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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

typealias HttpITestResult = ConnectionAndRequestResult<Unit, Unit>
typealias HttpITMeters = ConnectionAndRequestResult.Meters

/**
 * @author Eric Jessé
 */
@WithMockk
@Timeout(30)
internal class Http2ClientIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    lateinit var meterRegistry: CampaignMeterRegistry

    @RelaxedMockK
    private lateinit var ctx: StepContext<String, ConnectionAndRequestResult<String, ByteArray>>

    private val workerGroup = NativeTransportUtils.getEventLoopGroup()

    private val toRelease = mutableListOf<Any>()

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
        val client = HttpClient(3)
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

    @ParameterizedTest(name = "should connect to a server and perform 3 requests - {0}")
    @MethodSource("io.qalipsis.plugins.netty.http.client.Http2ClientIntegrationTest#allConfigurations")
    @Timeout(TIMEOUT_SECONDS)
    internal fun `should connect to a server and perform 3 requests`(
        name: String, configuration: HttpClientConfiguration, server: HttpServer
    ) = testDispatcherProvider.run {
        // given
        val monitoringCollector = spyk(HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry))

        // when
        val client = HttpClient(3)
        client.open(configuration, workerGroup, monitoringCollector)

        // then
        assertThat(client.isOpen).isTrue()
        assertThat(client.isExhausted()).isFalse()
        coVerifyOrder {
            monitoringCollector.recordConnecting()
            monitoringCollector.recordConnected(more(Duration.ZERO))
            if (server.secured) {
                monitoringCollector.recordTlsHandshakeSuccess(more(Duration.ZERO))
            }
            monitoringCollector.cause
        }
        var request = SimpleHttpRequest(HttpMethod.GET, "/status").apply {
            addParameter("status", "BAD_REQUEST")
        }

        // when
        var result = client.execute(ctx, request, monitoringCollector)

        // then
        assertThat(result.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST)
        assertThat(client.isOpen).isTrue()
        assertThat(client.isExhausted()).isFalse()
        coVerifyOnce {
            monitoringCollector.setTags(
                "protocol" to HTTP_2_0.protocol,
                "method" to "GET",
                "scheme" to if (server.secured) "https" else "http",
                "host" to "localhost",
                "port" to "${server.port}",
                "path" to "/status"
            )
            monitoringCollector.recordHttpStatus(HttpResponseStatus.BAD_REQUEST)
        }

        // when
        request = SimpleHttpRequest(HttpMethod.POST, "/status").apply {
            addParameter("status", "FORBIDDEN")
        }
        result = client.execute(ctx, request, monitoringCollector)

        // then
        assertThat(result.status()).isEqualTo(HttpResponseStatus.FORBIDDEN)
        assertThat(client.isOpen).isTrue()
        assertThat(client.isExhausted()).isFalse()
        coVerifyOnce {
            monitoringCollector.setTags(
                "protocol" to HTTP_2_0.protocol,
                "method" to "GET",
                "scheme" to if (server.secured) "https" else "http",
                "host" to "localhost",
                "port" to "${server.port}",
                "path" to "/status"
            )
            monitoringCollector.recordHttpStatus(HttpResponseStatus.FORBIDDEN)
        }

        // when
        request = SimpleHttpRequest(HttpMethod.DELETE, "/status").apply {
            addParameter("status", "NOT_FOUND")
        }
        result = client.execute(ctx, request, monitoringCollector)

        // then
        assertThat(result.status()).isEqualTo(HttpResponseStatus.NOT_FOUND)
        assertThat(client.isOpen).isTrue()
        assertThat(client.isExhausted()).isTrue()
        coVerifyOnce {
            monitoringCollector.setTags(
                "protocol" to HTTP_2_0.protocol,
                "method" to "GET",
                "scheme" to if (server.secured) "https" else "http",
                "host" to "localhost",
                "port" to "${server.port}",
                "path" to "/status"
            )
            monitoringCollector.recordHttpStatus(HttpResponseStatus.NOT_FOUND)
        }

        client.close()
    }


    @Test
    @Timeout(TIMEOUT_SECONDS)
    internal fun `should fail when connecting to a server with invalid port`() = testDispatcherProvider.run {
        // given
        val monitoringCollector = spyk(HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry))
        val client = HttpClient(3)
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
        val client = HttpClient(3)
        val exception = assertThrows<NotSslRecordException> {
            client.open(
                clientConfiguration().apply {
                    url("https://localhost:${plainServerHttp.port}")
                    connectTimeout = Duration.ofMillis(3000)
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
                    isLessThan(Duration.ofSeconds(4))
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
        val tempHttpServer = HttpServer.new(version = HTTP_2_0).apply { start() }
        val monitoringCollector = spyk(HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry))
        val client = HttpClient(3)
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
        val client = HttpClient(3)

        // when
        client.open(
            sslClientConfiguration().apply {
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
            monitoringCollector.recordConnecting()
            monitoringCollector.recordConnected(more(Duration.ZERO))
            monitoringCollector.recordTlsHandshakeSuccess(more(Duration.ZERO))
            monitoringCollector.cause
            monitoringCollector.setTags(
                "protocol" to HTTP_2_0.protocol,
                "method" to "GET",
                "scheme" to "https",
                "host" to "localhost",
                "port" to "${sslServerHttp.port}",
                "path" to "/delay"
            )
            monitoringCollector.recordSendingRequest()
        }
        coVerify(atLeast = 1) {
            monitoringCollector.recordSendingData(more(0))
            monitoringCollector.recordSentDataSuccess(more(0))
        }
        coVerifyOnce {
            monitoringCollector.recordSentRequestSuccess()
            monitoringCollector.recordReceivingDataFailure(refEq(exception))
        }

        client.close()

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
                prop(HttpITMeters::timeToSuccessfulTlsConnect).isNotNull().all {
                    isGreaterThan(Duration.ZERO)
                    isLessThan(Duration.ofSeconds(1))
                }
                prop(HttpITMeters::timeToFailedTlsConnect).isNull()
                prop(HttpITMeters::bytesCountToSend).isBetween(40, 90)
                prop(HttpITMeters::sentBytes).isBetween(40, 90)
                // Other fields are ignored.
            }
        }
    }

    @ParameterizedTest(name = "should GET a response - {0}")
    @MethodSource("io.qalipsis.plugins.netty.http.client.Http2ClientIntegrationTest#allConfigurations")
    @Timeout(TIMEOUT_SECONDS)
    internal fun `should GET a response`(
        name: String, configuration: HttpClientConfiguration, server: HttpServer
    ) = testDispatcherProvider.run {
        val monitoringCollector = spyk(HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry))

        val request = SimpleHttpRequest(HttpMethod.GET, "/get")
        request.addParameter("param1", "value1")
        request.addParameter("param1", "value2")
        request.addParameter("param2", "value3")
        request.addHeader(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
        request.addHeader(HttpHeaderNames.COOKIE, "yummy_cookie=choco; tasty_cookie=strawberry")

        val requestMetadata = exchange(configuration, server, ctx, monitoringCollector, request)
        assertThat(requestMetadata).all {
            prop(RequestMetadata::uri).isEqualTo(
                "/get?param1=value1&param1=value2&param2=value3"
            )
            prop(RequestMetadata::path).isEqualTo("/get")
            prop(RequestMetadata::version).isEqualTo("HTTP_2_0")
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
                key(HttpHeaderNames.HOST.toString()).isEqualTo("localhost:${configuration.port}")
                key("x-http2-scheme").isEqualTo(if (configuration.isSecure) "https" else "http")
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
                prop(HttpITMeters::bytesCountToSend).isBetween(140, 190)
                prop(HttpITMeters::sentBytes).isBetween(140, 190)
                prop(HttpITMeters::timeToFirstByte).isNotNull().all {
                    isGreaterThan(result.meters.timeToSuccessfulConnect)
                    isLessThan(Duration.ofSeconds(2))
                }
                prop(HttpITMeters::timeToLastByte).isNotNull().all {
                    isGreaterThan(result.meters.timeToFirstByte)
                    isLessThan(Duration.ofSeconds(4))
                }
                prop(HttpITMeters::receivedBytes).isBetween(490, 600)
            }
        }
    }

    @ParameterizedTest(name = "should PUT data - {0}")
    @MethodSource("io.qalipsis.plugins.netty.http.client.Http2ClientIntegrationTest#allConfigurations")
    @Timeout(TIMEOUT_SECONDS)
    internal fun `should PUT data`(
        name: String, configuration: HttpClientConfiguration, server: HttpServer
    ) = testDispatcherProvider.run {
        val monitoringCollector = spyk(HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry))

        val request = SimpleHttpRequest(HttpMethod.PUT, "/any/url")
        request.addParameter("param1", "value1")
        request.addParameter("param1", "value2")
        request.addParameter("param2", "value3")
        request.body("This is a test", HttpHeaderValues.TEXT_PLAIN)

        val requestMetadata = exchange(configuration, server, ctx, monitoringCollector, request)
        assertThat(requestMetadata).all {
            prop(RequestMetadata::uri).isEqualTo(
                "/any/url?param1=value1&param1=value2&param2=value3"
            )
            prop(RequestMetadata::path).isEqualTo("/any/url")
            prop(RequestMetadata::version).isEqualTo("HTTP_2_0")
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
                key(HttpHeaderNames.CONTENT_LENGTH.toString()).isEqualTo("14")
                key(HttpHeaderNames.HOST.toString()).isEqualTo("localhost:${configuration.port}")
                key("x-http2-scheme").isEqualTo(if (configuration.isSecure) "https" else "http")
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
                prop(HttpITMeters::bytesCountToSend).isBetween(140, 190)
                prop(HttpITMeters::sentBytes).isBetween(140, 190)
                prop(HttpITMeters::timeToFirstByte).isNotNull().all {
                    isGreaterThan(result.meters.timeToSuccessfulConnect)
                    isLessThan(Duration.ofSeconds(2))
                }
                prop(HttpITMeters::timeToLastByte).isNotNull().all {
                    isGreaterThan(result.meters.timeToFirstByte)
                    isLessThan(Duration.ofSeconds(4))
                }
                prop(HttpITMeters::receivedBytes).isBetween(485, 580)
            }
        }
    }

    @ParameterizedTest(name = "should POST a file - {0}")
    @MethodSource("io.qalipsis.plugins.netty.http.client.Http2ClientIntegrationTest#allConfigurations")
    @Timeout(TIMEOUT_SECONDS)
    internal fun `should POST a file`(
        name: String, configuration: HttpClientConfiguration, server: HttpServer
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

        val requestMetadata =
            exchange(configuration, server, ctx, monitoringCollector, request)
        assertThat(requestMetadata).all {
            prop(RequestMetadata::uri).isEqualTo("/")
            prop(RequestMetadata::path).isEqualTo("/")
            prop(RequestMetadata::version).isEqualTo("HTTP_2_0")
            prop(RequestMetadata::method).isEqualTo("POST")
            prop(RequestMetadata::parameters).isEmpty()
            prop(RequestMetadata::headers).all {
                key(HttpHeaderNames.HOST.toString()).isEqualTo("localhost:${configuration.port}")
                key(HttpHeaderNames.CONTENT_TYPE.toString()).startsWith("${HttpHeaderValues.MULTIPART_FORM_DATA}; boundary=")
                key("x-http2-scheme").isEqualTo(if (configuration.isSecure) "https" else "http")
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
                prop(HttpITMeters::bytesCountToSend).isBetween(550, 640)
                prop(HttpITMeters::sentBytes).isBetween(550, 640)
                prop(HttpITMeters::timeToFirstByte).isNotNull().all {
                    isGreaterThan(result.meters.timeToSuccessfulConnect)
                    isLessThan(Duration.ofSeconds(2))
                }
                prop(HttpITMeters::timeToLastByte).isNotNull().all {
                    isGreaterThan(result.meters.timeToFirstByte)
                    isLessThan(Duration.ofSeconds(4))
                }
                prop(HttpITMeters::receivedBytes).isBetween(490, 600)
            }
        }
    }


    @ParameterizedTest(name = "should PATCH a chunked request with a form - {0}")
    @MethodSource("io.qalipsis.plugins.netty.http.client.Http2ClientIntegrationTest#allConfigurations")
    @Timeout(TIMEOUT_SECONDS)
    internal fun `should PATCH a chunked request with a form`(
        name: String, configuration: HttpClientConfiguration, server: HttpServer
    ) = testDispatcherProvider.run {
        val monitoringCollector = spyk(HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry))

        val request = FormOrMultipartHttpRequest(HttpMethod.PATCH, "/send/form")
        request.addHeader(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
        request.addAttribute("info", "first value")
        request.addAttribute("secondinfo", "secondvalue ���&")
        request.addAttribute("thirdinfo", "Short text")
        request.addAttribute("fourthinfo", ("M".repeat(98) + "\r\n").repeat(100))

        val requestMetadata = exchange(configuration, server, ctx, monitoringCollector, request)

        assertThat(requestMetadata).all {
            prop(RequestMetadata::uri).isEqualTo("/send/form")
            prop(RequestMetadata::path).isEqualTo("/send/form")
            prop(RequestMetadata::version).isEqualTo("HTTP_2_0")
            prop(RequestMetadata::method).isEqualTo("PATCH")
            prop(RequestMetadata::parameters).isEmpty()
            prop(RequestMetadata::headers).all {
                key(HttpHeaderNames.ACCEPT.toString()).isEqualTo(HttpHeaderValues.APPLICATION_JSON.toString())
                key(HttpHeaderNames.HOST.toString()).isEqualTo("localhost:${configuration.port}")
                key(HttpHeaderNames.CONTENT_TYPE.toString()).isEqualTo(
                    HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString()
                )
                key("x-http2-scheme").isEqualTo(if (configuration.isSecure) "https" else "http")
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
                    isLessThan(Duration.ofSeconds(8))
                }
                prop(HttpITMeters::timeToLastByte).isNotNull().all {
                    isGreaterThan(result.meters.timeToFirstByte)
                    isLessThan(Duration.ofSeconds(8))
                }
                prop(HttpITMeters::receivedBytes).isBetween(10740, 10900)
            }
        }
    }

    @ParameterizedTest(name = "should GET a response - {0}")
    @MethodSource("io.qalipsis.plugins.netty.http.client.Http2ClientIntegrationTest#allConfigurations")
    @Timeout(TIMEOUT_SECONDS)
    internal fun `should add an authorization header to a request when specified`(
        name: String, configuration: HttpClientConfiguration, server: HttpServer
    ) = testDispatcherProvider.run {

        val request = SimpleHttpRequest(HttpMethod.GET, "/get")
        request.addParameter("param1", "value1")
        request.addParameter("param1", "value2")
        request.addParameter("param2", "value3")
        request.addHeader(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
        request.addHeader(HttpHeaderNames.COOKIE, "yummy_cookie=choco; tasty_cookie=strawberry")
        request.withBasicAuth("foo", "bar")

        val monitoringCollector = spyk(HttpStepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry))

        val requestMetadata = exchange(configuration, server, ctx, monitoringCollector, request)

        assertThat(requestMetadata).all {
            prop(RequestMetadata::headers).all {
                key(HttpHeaderNames.ACCEPT.toString()).isEqualTo(HttpHeaderValues.APPLICATION_JSON.toString())
                key(HttpHeaderNames.HOST.toString()).isEqualTo("localhost:${server.port}")
                key(HttpHeaderNames.AUTHORIZATION.toString()).isEqualTo("Basic Zm9vOmJhcg==")
            }
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
        }
    }

    private suspend fun exchange(
        configuration: HttpClientConfiguration,
        server: HttpServer,
        stepContext: StepContext<*, *>,
        monitoringCollector: HttpStepContextBasedSocketMonitoringCollector,
        request: io.qalipsis.plugins.netty.http.request.HttpRequest<*>
    ): RequestMetadata {
        val client = HttpClient(2)
        val result = try {
            client.open(configuration, workerGroup, monitoringCollector)
            client.execute(stepContext, request, monitoringCollector)
        } catch (e: Exception) {
            log.error { e.message }
            client.close()
            throw e
        }

        return try {
            log.trace { "Closing the temporary test HTTP client" }
            assertThat(client.isOpen).isTrue()
            client.close()

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
            // HTTP Cleartext is not supported.
            //Arguments.of("HTTP 2.0 clear text", clientConfiguration(), plainServerHttp),
            // HTTPS.
            Arguments.of("HTTP 2.0", sslClientConfiguration(), sslServerHttp)
        )

        @JvmStatic
        private fun sslClientConfiguration() = clientConfiguration(sslServerHttp)

        @JvmStatic
        private fun clientConfiguration(server: HttpServer = plainServerHttp): HttpClientConfiguration {
            return HttpClientConfiguration(
                version = HTTP_2_0,
                tlsConfiguration = TlsConfiguration(
                    disableCertificateVerification = true
                )
            ).apply {
                url(server.url)
                connectTimeout = Duration.ofSeconds(4)
                readTimeout = Duration.ofSeconds(8)
                shutdownTimeout = Duration.ofSeconds(3)
            }
        }

        @JvmField
        @RegisterExtension
        val plainServerHttp = HttpServer.new(version = HTTP_2_0)

        @JvmField
        @RegisterExtension
        val sslServerHttp = HttpServer.new(version = HTTP_2_0, enableTls = true)

        @JvmStatic
        val log = logger()

    }

}
