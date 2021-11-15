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

/**
 *
 * @author Eric Jessé
 */
@Testcontainers
class Http2ScenarioIntegrationTest {

    private var plainPort = 8080

    private lateinit var statsClient: HttpPunchingBallStatsClient

    @BeforeAll
    internal fun setUp() {
        plainPort = container.getMappedPort(8080)
        statsClient = HttpPunchingBallStatsClient(port = plainPort)
        Http2Scenario.httpPort = container.getMappedPort(8443)
    }

    @AfterEach
    internal fun tearDown() {
        statsClient.reset()
    }

    @Test
    @Timeout(30)
    internal fun `should run the HTTP scenario`() {
        val exitCode = QalipsisTestRunner.withScenarios("hello-netty-simple-http2-world").execute()

        Assertions.assertEquals(0, exitCode)

        val expectedHttpRequests = Http2Scenario.minions * (1 + Http2Scenario.repeat)
        val stats = statsClient.get()

        val httpCount = stats.count
        val httpDuration = stats.latestEpochMs - stats.earliestEpochMs
        log.info { "$httpCount HTTP requests in $httpDuration ms (${1000 * httpCount / httpDuration} req/s)" }

        Assertions.assertEquals(expectedHttpRequests, httpCount)
    }

    @Test
    @Timeout(30)
    internal fun `should run the HTTP scenario with pooling`() {
        val exitCode = QalipsisTestRunner.withScenarios("hello-netty-pooled-http2-world").execute()

        Assertions.assertEquals(0, exitCode)

        val expectedHttpRequests = Http2Scenario.pooledMinions * (1 + Http2Scenario.repeat)
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
            withCommand("--https")
            withExposedPorts(8080, 8443)
            waitingFor(HostPortWaitStrategy())
        }

        private val log = logger()
    }
}
