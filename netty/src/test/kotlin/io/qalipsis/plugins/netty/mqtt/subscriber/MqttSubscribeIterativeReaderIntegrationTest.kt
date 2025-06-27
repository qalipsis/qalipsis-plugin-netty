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

package io.qalipsis.plugins.netty.mqtt.subscriber

import assertk.all
import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.hasSize
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import io.aerisconsulting.catadioptre.getProperty
import io.netty.buffer.ByteBufUtil
import io.qalipsis.plugins.netty.mqtt.DEFAULT_MQTT_PORT
import io.qalipsis.plugins.netty.mqtt.MOSQUITTO_MQTT_IMAGE
import io.qalipsis.plugins.netty.mqtt.MqttClient
import io.qalipsis.plugins.netty.mqtt.MqttClientOptions
import io.qalipsis.plugins.netty.mqtt.spec.MqttAuthentication
import io.qalipsis.plugins.netty.mqtt.spec.MqttConnectionConfiguration
import io.qalipsis.plugins.netty.mqtt.spec.MqttQoS
import io.qalipsis.plugins.netty.mqtt.spec.MqttVersion
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.math.pow

@Testcontainers
internal class MqttSubscribeIterativeReaderIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private lateinit var reader: MqttSubscribeIterativeReader

    private lateinit var connectionConfiguration: MqttConnectionConfiguration

    @BeforeEach
    internal fun setUp() {
        connectionConfiguration =
            MqttConnectionConfiguration(container.host, container.getMappedPort(DEFAULT_MQTT_PORT))
    }

    @Test
    @Timeout(10)
    internal fun `should always have next at start but not at stop`() = testDispatcherProvider.run {
        val clientOptions = MqttClientOptions(
            connectionConfiguration = connectionConfiguration,
            authentication = MqttAuthentication(),
            clientId = "clientName",
            protocolVersion = MqttVersion.MQTT_3_1_1
        )

        reader = MqttSubscribeIterativeReader(clientOptions, 1, "stop", MqttQoS.AT_MOST_ONCE)

        reader.start(relaxedMockk())
        Assertions.assertTrue(reader.hasNext())

        reader.stop(relaxedMockk())
        Assertions.assertFalse(reader.hasNext())
    }

    @Test
    @Timeout(10)
    internal fun `should accept start after stop and consume`() = testDispatcherProvider.run {

        val clientOptions = MqttClientOptions(
            connectionConfiguration = connectionConfiguration,
            authentication = MqttAuthentication(),
            clientId = "clientName",
            protocolVersion = MqttVersion.MQTT_3_1_1
        )

        reader = MqttSubscribeIterativeReader(clientOptions, 1, "start/stop", MqttQoS.AT_MOST_ONCE)
        reader.start(relaxedMockk())
        val initialChannel = reader.getProperty<Channel<*>>("resultChannel")

        reader.stop(relaxedMockk())

        reader.start(relaxedMockk())
        val afterStopStartChannel = reader.getProperty<Channel<*>>("resultChannel")
        val mqttClient = reader.getProperty<MqttClient>("mqttClient")

        mqttClient.publish("start/stop", "1".toByteArray())

        val received = mutableListOf<String>()
        while (received.size < 1) {
            received.add(ByteBufUtil.getBytes(reader.next().payload()).decodeToString())
        }

        reader.stop(relaxedMockk())

        assertThat(afterStopStartChannel).isInstanceOf(Channel::class).isNotEqualTo(initialChannel)
        assertThat(received).all {
            hasSize(1)
            containsAll("1")
        }
    }

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