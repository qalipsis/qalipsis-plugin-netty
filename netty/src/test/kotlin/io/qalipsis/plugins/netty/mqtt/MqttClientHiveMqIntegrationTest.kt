package io.qalipsis.plugins.netty.mqtt

import org.junit.jupiter.api.Disabled
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.math.pow

@Testcontainers
@Disabled
internal class MqttClientHiveMqIntegrationTest: AbstractMqttClientIntegrationTest(container) {

    companion object {

        @Container
        @JvmStatic
        private val container = GenericContainer<Nothing>(HIVE_MQTT_IMAGE).apply {
            withExposedPorts(DEFAULT_MQTT_PORT, 8080)
            withCreateContainerCmdModifier {
                it.hostConfig!!.withMemory(512 * 1024.0.pow(2).toLong()).withCpuCount(2)
            }
            waitingFor(HostPortWaitStrategy())
        }
    }

}