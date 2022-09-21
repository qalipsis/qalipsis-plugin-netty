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

package io.qalipsis.plugins.netty.udp

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.plugins.netty.ServerUtils
import io.qalipsis.plugins.netty.netty
import io.qalipsis.plugins.netty.udp.spec.udp
import java.nio.charset.StandardCharsets

/**
 *
 * @author Eric Jessé
 */
object UdpScenario {

    const val minions = 20

    const val repeat = 5L

    val port = ServerUtils.availableUdpPort()

    private val request = "My UDP request".toByteArray(StandardCharsets.UTF_8)

    @Scenario("hello-netty-udp-world")
    fun myScenario() {
        scenario {
            minionsCount = minions
            profile {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }
            .start()
            .netty()
            .udp {
                iterations = repeat
                connect {
                    address("localhost", UdpScenario.port)
                }
                request { _, _ -> request }
            }
    }
}
