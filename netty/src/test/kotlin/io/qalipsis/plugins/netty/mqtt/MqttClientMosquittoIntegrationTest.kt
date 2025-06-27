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

package io.qalipsis.plugins.netty.mqtt

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.math.pow

@Testcontainers
internal class MqttClientMosquittoIntegrationTest: AbstractMqttClientIntegrationTest(container) {

    companion object {

        @Container
        @JvmStatic
        private val container = GenericContainer<Nothing>(MOSQUITTO_MQTT_IMAGE).apply {
            withExposedPorts(DEFAULT_MQTT_PORT)
            withCreateContainerCmdModifier {
                it.hostConfig!!.withMemory(256 * 1024.0.pow(2).toLong()).withCpuCount(2)
            }
            waitingFor(HostPortWaitStrategy())
        }
    }

}