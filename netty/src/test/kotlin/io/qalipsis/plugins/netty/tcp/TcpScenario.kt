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

import assertk.assertThat
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.verify
import io.qalipsis.plugins.netty.ServerUtils
import io.qalipsis.plugins.netty.netty
import io.qalipsis.plugins.netty.tcp.spec.closeTcp
import io.qalipsis.plugins.netty.tcp.spec.tcp
import io.qalipsis.plugins.netty.tcp.spec.tcpWith
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 *
 * @author Eric Jessé
 */
object TcpScenario {

    const val minions = 20

    const val pooledMinions = 200

    const val repeat = 100L

    val port = ServerUtils.availableTcpPort()

    private val request1 = "My first TCP request".toByteArray(StandardCharsets.UTF_8)

    private val request2 = "My second TCP request".toByteArray(StandardCharsets.UTF_8)

    @Scenario("hello-netty-simple-tcp-world")
    fun mySimpleScenario() {

        scenario {
            minionsCount = minions
            profile {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }
            .start()
            .netty()

            .tcp {
                name = "my-tcp"
                connect {
                    address("localhost", TcpScenario.port)
                    noDelay = true
                    keepConnectionAlive = true
                }
                request { _, _ -> request1 }
            }
            .verify {
                assertThat(it.meters.timeToSuccessfulConnect).isNotNull().isLessThan(Duration.ofSeconds(1))
            }
            .netty().tcpWith("my-tcp") {
                name = "reuse-tcp"
                request { _, _ -> request2 }
                iterate(repeat)
            }
            .closeTcp("my-tcp")
    }


    @Scenario("hello-netty-pooled-tcp-world")
    fun myPooledScenario() {

        scenario {
            minionsCount = pooledMinions
            profile {
                // Starts all at once.
                regular(100, pooledMinions)
            }
        }
            .start()
            .netty()

            .tcp {
                name = "my-tcp"
                connect {
                    address("localhost", TcpScenario.port)
                    noDelay = true
                }
                pool { size = 50 }
                request { _, _ -> request1 }
            }
            .verify {
                assertThat(it.meters.timeToFirstByte).isNotNull().isLessThan(Duration.ofSeconds(1))
            }
            .netty().tcpWith("my-tcp") {
                name = "reuse-tcp"
                request { _, _ -> request2 }
                iterate(repeat)
            }
    }
}
