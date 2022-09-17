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

    @Scenario
    fun mySimpleScenario() {

        scenario("hello-netty-simple-tcp-world") {
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


    @Scenario
    fun myPooledScenario() {

        scenario("hello-netty-pooled-tcp-world") {
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
