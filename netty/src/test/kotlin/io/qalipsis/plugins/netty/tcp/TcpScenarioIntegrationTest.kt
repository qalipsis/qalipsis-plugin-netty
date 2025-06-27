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

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.netty.tcp.server.TcpServer
import io.qalipsis.runtime.test.QalipsisTestRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 *
 * @author Eric Jessé
 */
class TcpScenarioIntegrationTest {

    @AfterEach
    internal fun tearDown() {
        requestsCount.set(0)
        firstRequest.set(0)
        lastRequest.set(0)
    }

    @Test
    @Timeout(30)
    internal fun `should run the TCP scenario`() {
        val exitCode =
            QalipsisTestRunner.withScenarios("hello-netty-simple-tcp-world").withEnvironments("scenario").execute()

        Assertions.assertEquals(0, exitCode)

        val expectedTcpRequests: Int = (TcpScenario.minions * (1 + TcpScenario.repeat)).toInt()
        val tcpCount = requestsCount.get()
        val tcpDuration = lastRequest.get() - firstRequest.get()
        log.info { "$tcpCount TCP requests in $tcpDuration ms (${1000 * tcpCount / tcpDuration} req/s)" }

        Assertions.assertEquals(expectedTcpRequests, requestsCount.get())
    }

    @Test
    @Timeout(30)
    internal fun `should run the TCP scenario with pooling`() {
        val exitCode =
            QalipsisTestRunner.withScenarios("hello-netty-pooled-tcp-world").withEnvironments("scenario").execute()

        Assertions.assertEquals(0, exitCode)

        val expectedTcpRequests: Int = (TcpScenario.pooledMinions * (1 + TcpScenario.repeat)).toInt()
        val tcpCount = requestsCount.get()
        val tcpDuration = lastRequest.get() - firstRequest.get()
        log.info { "$tcpCount TCP requests in $tcpDuration ms (${1000 * tcpCount / tcpDuration} req/s)" }

        Assertions.assertEquals(expectedTcpRequests, requestsCount.get())
    }

    companion object {

        private val requestsCount = AtomicInteger(0)

        private var firstRequest = AtomicLong(0)

        private var lastRequest = AtomicLong(0)

        @JvmField
        @RegisterExtension
        val plainTcpServer = TcpServer.new(port = TcpScenario.port) {
            lastRequest.set(System.currentTimeMillis())
            if (firstRequest.get() < 1L) {
                firstRequest.set(System.currentTimeMillis())
            }
            requestsCount.incrementAndGet()

            it
        }

        private val log = logger()
    }
}
