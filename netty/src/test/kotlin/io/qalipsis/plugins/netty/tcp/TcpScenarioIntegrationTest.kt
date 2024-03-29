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
