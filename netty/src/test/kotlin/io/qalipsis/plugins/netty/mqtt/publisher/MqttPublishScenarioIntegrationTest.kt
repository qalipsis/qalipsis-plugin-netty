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

package io.qalipsis.plugins.netty.mqtt.publisher

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import io.mockk.spyk
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.mqtt.MqttPublishMessage
import io.netty.handler.codec.mqtt.MqttQoS
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.mqtt.DEFAULT_MQTT_PORT
import io.qalipsis.plugins.netty.mqtt.MOSQUITTO_MQTT_IMAGE
import io.qalipsis.plugins.netty.mqtt.MqttClient
import io.qalipsis.plugins.netty.mqtt.MqttClientOptions
import io.qalipsis.plugins.netty.mqtt.MqttSubscriber
import io.qalipsis.plugins.netty.mqtt.spec.MqttAuthentication
import io.qalipsis.plugins.netty.mqtt.spec.MqttConnectionConfiguration
import io.qalipsis.plugins.netty.mqtt.spec.MqttVersion
import io.qalipsis.runtime.test.QalipsisTestRunner
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.verifyNever
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.CountDownLatch
import kotlin.math.pow

/**
 * @author Gabriel Moraes
 */
@Testcontainers
internal class MqttPublishScenarioIntegrationTest {

    private lateinit var clientOptions: MqttClientOptions

    @BeforeAll
    internal fun setUp() {
        val port = container.getMappedPort(DEFAULT_MQTT_PORT)
        MqttPublishScenario.portContainer = port
        MqttPublishScenario.hostContainer = container.host

        clientOptions = MqttClientOptions(
            connectionConfiguration = MqttConnectionConfiguration(container.host, port),
            authentication = MqttAuthentication(),
            clientId = "clientTest",
            protocolVersion = MqttVersion.MQTT_3_1
        )
    }

    @Test
    @Timeout(20)
    internal fun `should consume mqtt topic and publish into another`() {
        val workerGroup = spyk(NativeTransportUtils.getEventLoopGroup())
        MqttPublishScenario.receivedMessages.clear()
        val topicName = "test"
        val client = MqttClient(clientOptions, workerGroup)

        client.publish(topicName, "10".toByteArray(), qoS = MqttQoS.EXACTLY_ONCE)

        val exitCode = QalipsisTestRunner.withScenarios("publisher-mqtt").withEnvironments("scenario").execute()

        Assertions.assertEquals(0, exitCode)

        val latch = CountDownLatch(1)
        val messagesReceived = mutableListOf<MqttPublishMessage>()
        client.subscribe(MqttSubscriber("publisher/topic", qoS = MqttQoS.EXACTLY_ONCE) {
            messagesReceived.add(it)
            latch.countDown()
        })

        latch.await()

        client.close()
        assertThat(messagesReceived).all {
            hasSize(1)
            index(0).all {
                prop("payload").transform { ByteBufUtil.getBytes(it as ByteBuf).decodeToString() }.isEqualTo("10")
            }
        }
        // The shared worker event loop group should not be closed when a single client is closed.
        verifyNever {
            workerGroup.shutdownGracefully()
            workerGroup.shutdown()
            workerGroup.shutdownGracefully(any(), any(), any())
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