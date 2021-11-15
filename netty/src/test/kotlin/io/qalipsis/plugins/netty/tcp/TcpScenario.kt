package io.qalipsis.plugins.netty.tcp

import assertk.assertThat
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.rampup.regular
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
            rampUp {
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
            rampUp {
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
