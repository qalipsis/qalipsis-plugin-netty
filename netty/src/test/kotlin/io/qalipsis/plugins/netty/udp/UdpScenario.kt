package io.qalipsis.plugins.netty.udp

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.rampup.regular
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

    @Scenario
    fun myScenario() {
        scenario("hello-netty-udp-world") {
            minionsCount = minions
            rampUp {
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
