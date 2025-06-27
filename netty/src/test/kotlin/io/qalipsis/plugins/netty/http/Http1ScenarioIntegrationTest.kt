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

package io.qalipsis.plugins.netty.http

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.runtime.test.QalipsisTestRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.math.pow

/**
 *
 * @author Eric Jessé
 */
@Testcontainers
class Http1ScenarioIntegrationTest {

    private var serverPort = -1

    private lateinit var statsClient: HttpPunchingBallStatsClient

    @BeforeAll
    internal fun setUp() {
        serverPort = container.getMappedPort(8080)
        statsClient = HttpPunchingBallStatsClient(port = serverPort)
        Http1Scenario.httpPort = serverPort
    }

    @AfterEach
    internal fun tearDown() {
        statsClient.reset()
    }

    @Test
    @Timeout(30)
    internal fun `should run the HTTP scenario`() {
        val exitCode = QalipsisTestRunner.withScenarios("hello-netty-simple-http1-world")
            .withEnvironments("scenario").execute()

        Assertions.assertEquals(0, exitCode)

        val expectedHttpRequests = Http1Scenario.minions * (1 + Http1Scenario.repeat)
        val stats = statsClient.get()

        val httpCount = stats.requestsCount
        val httpDuration = stats.latestEpochMs - stats.earliestEpochMs
        log.info { "$httpCount HTTP requests in $httpDuration ms (${1000 * httpCount / httpDuration} req/s)" }

        Assertions.assertEquals(expectedHttpRequests, httpCount)
    }

    @Test
    @Timeout(30)
    internal fun `should run the HTTP scenario with pooling`() {
        val exitCode =
            QalipsisTestRunner.withScenarios("hello-netty-pooled-http1-world").withEnvironments("scenario").execute()

        Assertions.assertEquals(0, exitCode)

        val expectedHttpRequests = Http1Scenario.pooledMinions * (1 + Http1Scenario.repeat)
        val stats = statsClient.get()

        val httpCount = stats.requestsCount
        val httpDuration = stats.latestEpochMs - stats.earliestEpochMs
        log.info { "$httpCount HTTP requests in $httpDuration ms (${1000 * httpCount / httpDuration} req/s)" }

        Assertions.assertEquals(expectedHttpRequests, httpCount)
    }

    companion object {

        @Container
        @JvmStatic
        val container = GenericContainer<Nothing>("aerisconsulting/http-punching-ball:1.0.1").apply {
            withExposedPorts(8080)
            withCreateContainerCmdModifier { cmd ->
                cmd
                    //.withPlatform("linux/amd64")
                    .hostConfig!!
                    .withMemory(128 * 512.0.pow(2).toLong())
                    .withCpuCount(2)
            }
            waitingFor(HostPortWaitStrategy())
        }

        private val log = logger()
    }
}
