package io.qalipsis.plugins.netty.http

import assertk.assertThat
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import io.netty.handler.codec.http.HttpMethod
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.rampup.regular
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

    @Scenario
    fun mySimpleScenario() {

        scenario("hello-netty-simple-http1-world") {
            minionsCount = minions
            rampUp {
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


    @Scenario
    fun myPooledScenario() {

        scenario("hello-netty-pooled-http1-world") {
            minionsCount = pooledMinions
            rampUp {
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
