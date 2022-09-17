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

import assertk.assertThat
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import io.netty.handler.codec.http.HttpMethod
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.verify
import io.qalipsis.plugins.netty.http.request.SimpleHttpRequest
import io.qalipsis.plugins.netty.http.spec.HttpVersion
import io.qalipsis.plugins.netty.http.spec.closeHttp
import io.qalipsis.plugins.netty.http.spec.http
import io.qalipsis.plugins.netty.http.spec.httpWith
import io.qalipsis.plugins.netty.netty
import java.time.Duration

/**
 *
 * @author Eric Jessé
 */
object Http2Scenario {

    const val minions = 20

    const val pooledMinions = 200

    const val poolSize = 50

    const val repeat = 50L

    var httpPort: Int = 0

    private val request1 = SimpleHttpRequest(HttpMethod.GET, "/")

    private val request2 = SimpleHttpRequest(HttpMethod.POST, "/").body("This is my content")

    @Scenario
    fun mySimpleScenario() {

        scenario("hello-netty-simple-http2-world") {
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
                    version = HttpVersion.HTTP_2_0
                    url("https://localhost:${httpPort}/")
                    noDelay = true
                    keepConnectionAlive = true
                    tls {
                        disableCertificateVerification = true
                    }
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

    @Scenario
    fun myPooledScenario() {

        scenario("hello-netty-pooled-http2-world") {
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
                    version = HttpVersion.HTTP_2_0
                    url("https://localhost:${httpPort}/")
                    noDelay = true
                    tls {
                        disableCertificateVerification = true
                    }
                }
                pool { size = poolSize }
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
