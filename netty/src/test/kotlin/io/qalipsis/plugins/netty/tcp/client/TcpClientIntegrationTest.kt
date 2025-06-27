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

package io.qalipsis.plugins.netty.tcp.client

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Timer
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.Server
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.proxy.server.ProxyServer
import io.qalipsis.plugins.netty.tcp.ConnectionAndRequestResult
import io.qalipsis.plugins.netty.tcp.server.TcpServer
import io.qalipsis.plugins.netty.tcp.spec.TcpClientConfiguration
import io.qalipsis.plugins.netty.tcp.spec.TcpProxyType
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.ConnectException
import java.net.SocketException
import java.nio.channels.ClosedChannelException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLHandshakeException

@WithMockk
internal class TcpClientIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    lateinit var meterRegistry: CampaignMeterRegistry

    @RelaxedMockK
    private lateinit var ctx: StepContext<String, ConnectionAndRequestResult<String, ByteArray>>

    private val clientsToClean = mutableListOf<TcpClient>()

    private val workerGroup = NativeTransportUtils.getEventLoopGroup()


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

    @AfterAll
    internal fun finalTearDown() {
        workerGroup.shutdownGracefully()
    }

    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run {
        clientsToClean.forEach { kotlin.runCatching { it.close() } }
        clientsToClean.clear()
    }

    @Nested
    inner class `Plain server` {

        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should connect to a server and properly close`() = testDispatcherProvider.run {
            // given
            val monitoringCollector =
                spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))

            // when
            val client = TcpClient(3).also(clientsToClean::add)
            client.open(
                TcpClientConfiguration().apply {
                    address("localhost", plainServer.port)
                    readTimeout = Duration.ofMillis(500)
                    shutdownTimeout = Duration.ofMillis(500)
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

            // when
            client.close()

            // then
            assertThat(client.isOpen).isFalse()
            assertThat(client.isExhausted()).isFalse()
        }

        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should connect to a server, receive the echo response for 3 usages`() =
            testDispatcherProvider.run {
                // given
                val monitoringCollector =
                    spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))

                // when
                val client = TcpClient(3).also(clientsToClean::add)
                client.open(
                    TcpClientConfiguration().apply {
                        address("localhost", plainServer.port)
                        readTimeout = Duration.ofMillis(500)
                        shutdownTimeout = Duration.ofMillis(500)
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

                // when
                var result = client.execute(ctx, "ABC".toByteArray(StandardCharsets.UTF_8), monitoringCollector)

                // then
                assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo("Received ABC")
                assertThat(client.isOpen).isTrue()
                assertThat(client.isExhausted()).isFalse()
                coVerifyOrder {
                    monitoringCollector.recordSendingData(eq(3))
                    monitoringCollector.recordSentDataSuccess(eq(3))
                    monitoringCollector.recordReceivingData()
                    monitoringCollector.countReceivedData(eq(12))
                    monitoringCollector.recordReceptionComplete()
                }
                confirmVerified(monitoringCollector)

                // when
                result = client.execute(ctx, "ANY".toByteArray(StandardCharsets.UTF_8), monitoringCollector)

                // then
                assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo("Received ANY")
                assertThat(client.isOpen).isTrue()
                assertThat(client.isExhausted()).isFalse()

                // when
                result = client.execute(ctx, "OTHER".toByteArray(StandardCharsets.UTF_8), monitoringCollector)

                // then
                assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo("Received OTHER")
                assertThat(client.isOpen).isTrue()
                assertThat(client.isExhausted()).isTrue()
            }

        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should fail when connecting to a server with invalid port`() = testDispatcherProvider.run {
            // given
            val monitoringCollector =
                spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))
            val client = TcpClient(3).also(clientsToClean::add)
            val exception = assertThrows<ConnectException> {
                client.open(
                    TcpClientConfiguration().apply {
                        address("localhost", 12133)
                        connectTimeout = Duration.ofMillis(500)
                        readTimeout = Duration.ofMillis(100)
                        shutdownTimeout = Duration.ofMillis(100)
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
        }

        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should connect to a server that dies after connection`() = testDispatcherProvider.run {
            // given
            val tempTcpServer = TcpServer.new(handler = SERVER_HANDLER).also { it.start() }
            val monitoringCollector =
                spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))
            val client = TcpClient(3).also(clientsToClean::add)

            client.open(
                TcpClientConfiguration().apply {
                    address("localhost", tempTcpServer.port)
                    readTimeout = Duration.ofMillis(500)
                    shutdownTimeout = Duration.ofMillis(500)
                },
                workerGroup,
                monitoringCollector
            )
            tempTcpServer.stop()
            // Add a delay to ensure the server channels and workers are closed.
            delay(1000)

            // when
            assertThrows<ClosedChannelException> {
                client.execute(ctx, "ABC".toByteArray(StandardCharsets.UTF_8), monitoringCollector)
            }

            // then
            assertThat(client.isExhausted()).isFalse()
            coVerify {
                monitoringCollector.recordConnecting()
                monitoringCollector.recordConnected(more(Duration.ZERO))
                monitoringCollector.cause
                monitoringCollector.recordSendingData(eq(3))
                monitoringCollector.recordSentDataFailure(any<SocketException>())
            }
        }

        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should connect to a server that never answers`() = testDispatcherProvider.run {
            // given
            val serverRef = AtomicReference<Server>()
            val tempTcpServer = TcpServer.new(handler = { serverRef.get().stop(); throw RuntimeException() }).also {
                serverRef.set(it)
                it.start()
            }
            val monitoringCollector =
                spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))
            val client = TcpClient(3).also(clientsToClean::add)

            // when
            client.open(
                TcpClientConfiguration().apply {
                    address("localhost", tempTcpServer.port)
                    readTimeout = Duration.ofMillis(500)
                    shutdownTimeout = Duration.ofMillis(500)
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
            val exception = assertThrows<TimeoutException> {
                client.execute(ctx, "ABC".toByteArray(StandardCharsets.UTF_8), monitoringCollector)
            }

            // then
            assertThat(client.isExhausted()).isFalse()
            coVerifyOrder {
                monitoringCollector.recordSendingData(eq(3))
                monitoringCollector.recordSentDataSuccess(eq(3))
                monitoringCollector.recordReceivingDataFailure(refEq(exception))
            }

            confirmVerified(monitoringCollector)
        }
    }

    @Nested
    inner class `TLS server` {

        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should connect to a server and properly close`() = testDispatcherProvider.run {
            // given
            val monitoringCollector =
                spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))

            // when
            val client = TcpClient(3).also(clientsToClean::add)
            client.open(
                TcpClientConfiguration().apply {
                    address("localhost", tlsServer.port)
                    readTimeout = Duration.ofMillis(500)
                    shutdownTimeout = Duration.ofMillis(500)
                    tls {
                        disableCertificateVerification = true
                    }
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
                monitoringCollector.recordTlsHandshakeSuccess(more(Duration.ZERO))
                monitoringCollector.cause
            }

            // when
            client.close()

            // then
            assertThat(client.isOpen).isFalse()
            assertThat(client.isExhausted()).isFalse()
        }

        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should connect to a server, receive the echo response for 3 usages`() =
            testDispatcherProvider.run {
                // given
                val monitoringCollector =
                    spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))

                // when
                val client = TcpClient(3).also(clientsToClean::add)
                client.open(
                    TcpClientConfiguration().apply {
                        address("localhost", tlsServer.port)
                        readTimeout = Duration.ofMillis(500)
                        shutdownTimeout = Duration.ofMillis(500)
                        tls {
                            disableCertificateVerification = true
                        }
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
                    monitoringCollector.recordTlsHandshakeSuccess(more(Duration.ZERO))
                    monitoringCollector.cause
                }

                // when
                var result = client.execute(ctx, "ABC".toByteArray(StandardCharsets.UTF_8), monitoringCollector)

                // then
                assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo("Received ABC")
                assertThat(client.isOpen).isTrue()
                assertThat(client.isExhausted()).isFalse()
                coVerifyOrder {
                    monitoringCollector.recordSendingData(eq(3))
                    monitoringCollector.recordSentDataSuccess(eq(3))
                    monitoringCollector.recordReceivingData()
                    monitoringCollector.countReceivedData(eq(12))
                    monitoringCollector.recordReceptionComplete()
                }
                confirmVerified(monitoringCollector)

                // when
                result = client.execute(ctx, "ANY".toByteArray(StandardCharsets.UTF_8), monitoringCollector)

                // then
                assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo("Received ANY")
                assertThat(client.isOpen).isTrue()
                assertThat(client.isExhausted()).isFalse()

                // when
                result = client.execute(ctx, "OTHER".toByteArray(StandardCharsets.UTF_8), monitoringCollector)

                // then
                assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo("Received OTHER")
                assertThat(client.isOpen).isTrue()
                assertThat(client.isExhausted()).isTrue()
            }

        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should fail when connecting to a server with invalid port`() = testDispatcherProvider.run {
            // given
            val monitoringCollector =
                spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))
            val client = TcpClient(3).also(clientsToClean::add)
            val exception = assertThrows<ConnectException> {
                client.open(
                    TcpClientConfiguration().apply {
                        address("localhost", 12133)
                        readTimeout = Duration.ofMillis(100)
                        shutdownTimeout = Duration.ofMillis(100)
                        connectTimeout = Duration.ofMillis(500)
                        tls {
                            disableCertificateVerification = true
                        }
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
        }


        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should fail when connecting to a server enabling certificate validation`() =
            testDispatcherProvider.run {
                // given
                val monitoringCollector =
                    spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))
                val client = TcpClient(3).also(clientsToClean::add)
                val exception = assertThrows<SSLHandshakeException> {
                    client.open(
                        TcpClientConfiguration().apply {
                            address("localhost", tlsServer.port)
                            readTimeout = Duration.ofMillis(500)
                            shutdownTimeout = Duration.ofMillis(500)
                            tls {
                                disableCertificateVerification = false
                            }
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
            }

        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should fail when connecting to a server without TLS configuration`() =
            testDispatcherProvider.run {
                // given
                val monitoringCollector =
                    spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))
                val client = TcpClient(3).also(clientsToClean::add)
                client.open(
                    TcpClientConfiguration().apply {
                        address("localhost", tlsServer.port)
                        readTimeout = Duration.ofMillis(500)
                        shutdownTimeout = Duration.ofMillis(500)
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

                confirmVerified(monitoringCollector)

                val exception = assertThrows<TimeoutException> {
                    client.execute(ctx, "ABC".toByteArray(StandardCharsets.UTF_8), monitoringCollector)
                }

                // then
                assertThat(client.isExhausted()).isFalse()
                coVerifyOrder {
                    monitoringCollector.recordSendingData(eq(3))
                    monitoringCollector.recordSentDataSuccess(eq(3))
                    monitoringCollector.recordReceivingDataFailure(refEq(exception))
                }

                confirmVerified(monitoringCollector)
            }
    }

    @Nested
    inner class `Socks proxies` {


        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should connect to a server with SOCKS 4 and properly close`() = testDispatcherProvider.run {
            // given
            val monitoringCollector =
                spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))

            // when
            val client = TcpClient(3).also(clientsToClean::add)
            client.open(
                TcpClientConfiguration().apply {
                    address("localhost", plainServer.port)
                    readTimeout = Duration.ofMillis(500)
                    shutdownTimeout = Duration.ofMillis(500)
                    proxy {
                        type = TcpProxyType.SOCKS4
                        address("localhost", socksProxyServer.port)
                    }
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

            // when
            client.close()

            // then
            assertThat(client.isOpen).isFalse()
            assertThat(client.isExhausted()).isFalse()
        }

        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should connect to a server with SOCKS 4, receive the echo response for 3 usages`() =
            testDispatcherProvider.run {
                // given
                val monitoringCollector =
                    spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))

                // when
                val client = TcpClient(3).also(clientsToClean::add)
                client.open(
                    TcpClientConfiguration().apply {
                        address("localhost", plainServer.port)
                        readTimeout = Duration.ofMillis(500)
                        shutdownTimeout = Duration.ofMillis(500)
                        proxy {
                            type = TcpProxyType.SOCKS4
                            address("localhost", socksProxyServer.port)
                        }
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

                // when
                var result = client.execute(ctx, "ABC".toByteArray(StandardCharsets.UTF_8), monitoringCollector)

                // then
                assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo("Received ABC")
                assertThat(client.isOpen).isTrue()
                assertThat(client.isExhausted()).isFalse()
                coVerifyOrder {
                    monitoringCollector.recordSendingData(eq(3))
                    monitoringCollector.recordSentDataSuccess(eq(3))
                    monitoringCollector.recordReceivingData()
                    monitoringCollector.countReceivedData(eq(12))
                    monitoringCollector.recordReceptionComplete()
                }
                confirmVerified(monitoringCollector)

                // when
                result = client.execute(ctx, "ANY".toByteArray(StandardCharsets.UTF_8), monitoringCollector)

                // then
                assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo("Received ANY")
                assertThat(client.isOpen).isTrue()
                assertThat(client.isExhausted()).isFalse()

                // when
                result = client.execute(ctx, "OTHER".toByteArray(StandardCharsets.UTF_8), monitoringCollector)

                // then
                assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo("Received OTHER")
                assertThat(client.isOpen).isTrue()
                assertThat(client.isExhausted()).isTrue()
            }


        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should connect to a server with SOCKS 5 and properly close`() = testDispatcherProvider.run {
            // given
            val monitoringCollector =
                spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))

            // when
            val client = TcpClient(3).also(clientsToClean::add)
            client.open(
                TcpClientConfiguration().apply {
                    address("localhost", plainServer.port)
                    readTimeout = Duration.ofMillis(500)
                    shutdownTimeout = Duration.ofMillis(500)
                    proxy {
                        type = TcpProxyType.SOCKS5
                        address("localhost", socksProxyServer.port)
                    }
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

            // when
            client.close()

            // then
            assertThat(client.isOpen).isFalse()
            assertThat(client.isExhausted()).isFalse()
        }

        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should connect to a server with SOCKS 5, receive the echo response for 3 usages`() =
            testDispatcherProvider.run {
                // given
                val monitoringCollector =
                    spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))

                // when
                val client = TcpClient(3).also(clientsToClean::add)
                client.open(
                    TcpClientConfiguration().apply {
                        address("localhost", plainServer.port)
                        readTimeout = Duration.ofMillis(500)
                        shutdownTimeout = Duration.ofMillis(500)
                        proxy {
                            type = TcpProxyType.SOCKS5
                            address("localhost", socksProxyServer.port)
                        }
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

                // when
                var result = client.execute(ctx, "ABC".toByteArray(StandardCharsets.UTF_8), monitoringCollector)

                // then
                assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo("Received ABC")
                assertThat(client.isOpen).isTrue()
                assertThat(client.isExhausted()).isFalse()
                coVerifyOrder {
                    monitoringCollector.recordSendingData(eq(3))
                    monitoringCollector.recordSentDataSuccess(eq(3))
                    monitoringCollector.recordReceivingData()
                    monitoringCollector.countReceivedData(eq(12))
                    monitoringCollector.recordReceptionComplete()
                }
                confirmVerified(monitoringCollector)

                // when
                result = client.execute(ctx, "ANY".toByteArray(StandardCharsets.UTF_8), monitoringCollector)

                // then
                assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo("Received ANY")
                assertThat(client.isOpen).isTrue()
                assertThat(client.isExhausted()).isFalse()

                // when
                result = client.execute(ctx, "OTHER".toByteArray(StandardCharsets.UTF_8), monitoringCollector)

                // then
                assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo("Received OTHER")
                assertThat(client.isOpen).isTrue()
                assertThat(client.isExhausted()).isTrue()
            }

        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should fail when connecting to a valid server with invalid Socks port`() =
            testDispatcherProvider.run {
                // given
                val monitoringCollector =
                    spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))
                val client = TcpClient(3).also(clientsToClean::add)
                val exception = assertThrows<ConnectException> {
                    client.open(
                        TcpClientConfiguration().apply {
                            address("localhost", plainServer.port)
                            readTimeout = Duration.ofMillis(100)
                            shutdownTimeout = Duration.ofMillis(100)
                            connectTimeout = Duration.ofMillis(500)
                            proxy {
                                type = TcpProxyType.SOCKS5
                                address("localhost", 12133)
                            }
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
            }
    }

    @Nested
    inner class `Socks proxies with TLS` {

        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should connect to a server with SOCKS 4 and properly close`() = testDispatcherProvider.run {
            // given
            val monitoringCollector =
                spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))

            // when
            val client = TcpClient(3).also(clientsToClean::add)
            client.open(
                TcpClientConfiguration().apply {
                    address("localhost", tlsServer.port)
                    readTimeout = Duration.ofMillis(500)
                    shutdownTimeout = Duration.ofMillis(500)
                    tls {
                        disableCertificateVerification = true
                    }
                    proxy {
                        type = TcpProxyType.SOCKS4
                        address("localhost", socksProxyServer.port)
                    }
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
                monitoringCollector.recordTlsHandshakeSuccess(more(Duration.ZERO))
                monitoringCollector.cause
            }

            // when
            client.close()

            // then
            assertThat(client.isOpen).isFalse()
            assertThat(client.isExhausted()).isFalse()
        }

        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should connect to a server with SOCKS 4, receive the echo response for 3 usages`() =
            testDispatcherProvider.run {
                // given
                val monitoringCollector =
                    spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))

                // when
                val client = TcpClient(3).also(clientsToClean::add)
                client.open(
                    TcpClientConfiguration().apply {
                        address("localhost", tlsServer.port)
                        readTimeout = Duration.ofMillis(500)
                        shutdownTimeout = Duration.ofMillis(500)
                        tls {
                            disableCertificateVerification = true
                        }
                        proxy {
                            type = TcpProxyType.SOCKS4
                            address("localhost", socksProxyServer.port)
                        }
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
                    monitoringCollector.recordTlsHandshakeSuccess(more(Duration.ZERO))
                    monitoringCollector.cause
                }

                // when
                var result = client.execute(ctx, "ABC".toByteArray(StandardCharsets.UTF_8), monitoringCollector)

                // then
                assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo("Received ABC")
                assertThat(client.isOpen).isTrue()
                assertThat(client.isExhausted()).isFalse()
                coVerifyOrder {
                    monitoringCollector.recordSendingData(eq(3))
                    monitoringCollector.recordSentDataSuccess(eq(3))
                    monitoringCollector.recordReceivingData()
                    monitoringCollector.countReceivedData(eq(12))
                    monitoringCollector.recordReceptionComplete()
                }
                confirmVerified(monitoringCollector)

                // when
                result = client.execute(ctx, "ANY".toByteArray(StandardCharsets.UTF_8), monitoringCollector)

                // then
                assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo("Received ANY")
                assertThat(client.isOpen).isTrue()
                assertThat(client.isExhausted()).isFalse()

                // when
                result = client.execute(ctx, "OTHER".toByteArray(StandardCharsets.UTF_8), monitoringCollector)

                // then
                assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo("Received OTHER")
                assertThat(client.isOpen).isTrue()
                assertThat(client.isExhausted()).isTrue()
            }

        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should connect to a server with SOCKS 5 and properly close`() = testDispatcherProvider.run {
            // given
            val monitoringCollector =
                spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))

            // when
            val client = TcpClient(3).also(clientsToClean::add)
            client.open(
                TcpClientConfiguration().apply {
                    address("localhost", tlsServer.port)
                    readTimeout = Duration.ofMillis(500)
                    shutdownTimeout = Duration.ofMillis(500)
                    tls {
                        disableCertificateVerification = true
                    }
                    proxy {
                        type = TcpProxyType.SOCKS5
                        address("localhost", socksProxyServer.port)
                    }
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
                monitoringCollector.recordTlsHandshakeSuccess(more(Duration.ZERO))
                monitoringCollector.cause
            }

            // when
            client.close()

            // then
            assertThat(client.isOpen).isFalse()
            assertThat(client.isExhausted()).isFalse()
        }

        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should connect to a server with SOCKS 5, receive the echo response for 3 usages`() =
            testDispatcherProvider.run {
                // given
                val monitoringCollector =
                    spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))

                // when
                val client = TcpClient(3).also(clientsToClean::add)
                client.open(
                    TcpClientConfiguration().apply {
                        address("localhost", tlsServer.port)
                        readTimeout = Duration.ofMillis(500)
                        shutdownTimeout = Duration.ofMillis(500)
                        tls {
                            disableCertificateVerification = true
                        }
                        proxy {
                            type = TcpProxyType.SOCKS5
                            address("localhost", socksProxyServer.port)
                        }
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
                    monitoringCollector.recordTlsHandshakeSuccess(more(Duration.ZERO))
                    monitoringCollector.cause
                }

                // when
                var result = client.execute(ctx, "ABC".toByteArray(StandardCharsets.UTF_8), monitoringCollector)

                // then
                assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo("Received ABC")
                assertThat(client.isOpen).isTrue()
                assertThat(client.isExhausted()).isFalse()
                coVerifyOrder {
                    monitoringCollector.recordSendingData(eq(3))
                    monitoringCollector.recordSentDataSuccess(eq(3))
                    monitoringCollector.recordReceivingData()
                    monitoringCollector.countReceivedData(eq(12))
                    monitoringCollector.recordReceptionComplete()
                }
                confirmVerified(monitoringCollector)

                // when
                result = client.execute(ctx, "ANY".toByteArray(StandardCharsets.UTF_8), monitoringCollector)

                // then
                assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo("Received ANY")
                assertThat(client.isOpen).isTrue()
                assertThat(client.isExhausted()).isFalse()

                // when
                result = client.execute(ctx, "OTHER".toByteArray(StandardCharsets.UTF_8), monitoringCollector)

                // then
                assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo("Received OTHER")
                assertThat(client.isOpen).isTrue()
                assertThat(client.isExhausted()).isTrue()
            }

        @Test
        @Timeout(TIMEOUT_SECONDS)
        internal fun `should fail when connecting to a valid server with invalid Socks port`() =
            testDispatcherProvider.run {
                // given
                val monitoringCollector =
                    spyk(StepContextBasedSocketMonitoringCollector(ctx, eventsLogger, meterRegistry, "test"))
                val client = TcpClient(3).also(clientsToClean::add)
                val exception = assertThrows<ConnectException> {
                    client.open(
                        TcpClientConfiguration().apply {
                            address("localhost", tlsServer.port)
                            readTimeout = Duration.ofMillis(100)
                            shutdownTimeout = Duration.ofMillis(100)
                            connectTimeout = Duration.ofMillis(500)
                            tls {
                                disableCertificateVerification = true
                            }
                            proxy {
                                type = TcpProxyType.SOCKS5
                                address("localhost", 12133)
                            }
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
            }
    }

    companion object {

        init {
            System.setProperty("QALIPSIS_LOGGING_LEVEL", "trace")
        }

        private const val TIMEOUT_SECONDS = 5L

        private val SERVER_HANDLER: (ByteArray) -> ByteArray = {
            "Received ${it.toString(StandardCharsets.UTF_8)}".toByteArray(StandardCharsets.UTF_8)
        }

        @JvmField
        @RegisterExtension
        val tlsServer = TcpServer.new(enableTls = true, handler = SERVER_HANDLER)

        @JvmField
        @RegisterExtension
        val plainServer = TcpServer.new(handler = SERVER_HANDLER)

        @JvmField
        @RegisterExtension
        val socksProxyServer = ProxyServer.newSocks()
    }
}
