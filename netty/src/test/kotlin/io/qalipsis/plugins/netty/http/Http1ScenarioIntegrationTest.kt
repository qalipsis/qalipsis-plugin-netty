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

package io.qalipsis.plugins.netty.http

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
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

    val jsonMapper = jacksonMapperBuilder().also {
        it.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        it.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    }.build()

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

        val httpCount = stats.count
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

        val httpCount = stats.count
        val httpDuration = stats.latestEpochMs - stats.earliestEpochMs
        log.info { "$httpCount HTTP requests in $httpDuration ms (${1000 * httpCount / httpDuration} req/s)" }

        Assertions.assertEquals(expectedHttpRequests, httpCount)
    }

    companion object {

        @Container
        @JvmStatic
        val container = GenericContainer<Nothing>("aerisconsulting/http-punching-ball").apply {
            withExposedPorts(8080)
            withCreateContainerCmdModifier {
                it.hostConfig!!.withMemory(128 * 1024.0.pow(2).toLong()).withCpuCount(2)
            }
            waitingFor(HostPortWaitStrategy())
        }

        private val log = logger()
    }
}
