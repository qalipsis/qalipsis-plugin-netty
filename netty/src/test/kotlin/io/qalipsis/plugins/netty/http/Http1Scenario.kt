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

import assertk.assertThat
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import io.netty.handler.codec.http.HttpMethod
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.verify
import io.qalipsis.plugins.netty.http.request.SimpleHttpRequest
import io.qalipsis.plugins.netty.http.spec.closeHttp
import io.qalipsis.plugins.netty.http.spec.http
import io.qalipsis.plugins.netty.http.spec.httpWith
import io.qalipsis.plugins.netty.netty
import java.time.Duration

/**
 *
 * @author Eric Jessé
 */
object Http1Scenario {

    const val minions = 20

    const val pooledMinions = 200

    const val repeat = 50L

    var httpPort = 0

    private val request1 = SimpleHttpRequest(HttpMethod.GET, "/")

    private val request2 = SimpleHttpRequest(HttpMethod.POST, "/").body("This is my content")

    @Scenario("hello-netty-simple-http1-world")
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

            .http {
                name = "my-http"
                connect {
                    url("http://localhost:${httpPort}/")
                    noDelay = true
                    keepConnectionAlive = true
                }
                request { _, _ -> request1 }
            }
            .verify {
                assertThat(it.meters.timeToSuccessfulConnect).isNotNull().isLessThan(Duration.ofSeconds(1))
            }
            .netty().httpWith("my-http") {
                name = "reuse-http"
                request { _, _ -> request2 }
                iterate(repeat)
            }
            .closeHttp("my-http")
    }


    @Scenario("hello-netty-pooled-http1-world")
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

            .http {
                name = "my-http"
                connect {
                    url("http://localhost:${httpPort}/")
                    noDelay = true
                }
                pool { size = 50 }
                request { _, _ -> request1 }
            }
            .verify {
                assertThat(it.meters.timeToFirstByte).isNotNull().isLessThan(Duration.ofSeconds(1))
            }
            .netty().httpWith("my-http") {
                name = "reuse-http"
                request { _, _ -> request2 }
                iterate(repeat)
            }
    }
}
