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

package io.qalipsis.plugins.netty.udp

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.netty.udp.server.UdpServer
import io.qalipsis.runtime.test.QalipsisTestRunner
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
class UdpScenarioIntegrationTest {

    @Test
    @Timeout(30)
    internal fun `should run the UDP scenario`() {
        System.setProperty("QALIPSIS_LOGGING_LEVEL", "INFO")
        val exitCode = QalipsisTestRunner.withScenarios("hello-netty-udp-world").withEnvironments("scenario").execute()

        Assertions.assertEquals(0, exitCode)

        val expectedUdpRequests: Int = (UdpScenario.minions * UdpScenario.repeat).toInt()
        val udpCount = requestsCount.get()
        val udpDuration = lastRequest.get() - firstRequest.get()
        log.info { "$udpCount UDP requests in $udpDuration ms (${1000 * udpCount / udpDuration} req/s)" }

        Assertions.assertEquals(expectedUdpRequests, requestsCount.get())
    }

    companion object {

        private val requestsCount = AtomicInteger(0)

        private var firstRequest = AtomicLong(0)

        private var lastRequest = AtomicLong(0)

        @JvmField
        @RegisterExtension
        val plainUdpServer = UdpServer.new(port = UdpScenario.port) {
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
