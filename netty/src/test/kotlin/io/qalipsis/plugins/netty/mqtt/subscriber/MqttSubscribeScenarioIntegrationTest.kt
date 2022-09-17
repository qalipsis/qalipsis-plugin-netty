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

package io.qalipsis.plugins.netty.mqtt.subscriber

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import com.fasterxml.jackson.databind.json.JsonMapper
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.mqtt.DEFAULT_MQTT_PORT
import io.qalipsis.plugins.netty.mqtt.MOSQUITTO_MQTT_IMAGE
import io.qalipsis.plugins.netty.mqtt.MqttClient
import io.qalipsis.plugins.netty.mqtt.MqttClientOptions
import io.qalipsis.plugins.netty.mqtt.spec.MqttAuthentication
import io.qalipsis.plugins.netty.mqtt.spec.MqttConnectionConfiguration
import io.qalipsis.plugins.netty.mqtt.spec.MqttVersion
import io.qalipsis.runtime.test.QalipsisTestRunner
import io.qalipsis.test.coroutines.TestDispatcherProvider
import org.junit.jupiter.api.AfterEach
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

/**
 * @author Gabriel Moraes
 */
@Testcontainers
internal class MqttSubscribeScenarioIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private lateinit var client: MqttClient

    @BeforeEach
    internal fun setUp() {
        val port = container.getMappedPort(DEFAULT_MQTT_PORT)
        MqttSubscribeScenario.portContainer = port
        MqttSubscribeScenario.hostContainer = container.host

        val clientOptions = MqttClientOptions(
            connectionConfiguration = MqttConnectionConfiguration(container.host, port),
            authentication = MqttAuthentication(),
            clientId = "clientName",
            protocolVersion = MqttVersion.MQTT_3_1_1
        )

        client = MqttClient(clientOptions, NativeTransportUtils.getEventLoopGroup())
    }

    @AfterEach
    internal fun tearDown() {
        client.close()
    }

    @Test
    @Timeout(20)
    internal fun `should run the consumer scenario with string deserializer`() {
        MqttSubscribeScenario.receivedMessages.clear()
        val topicName = "test"
        client.publish(topicName, "10".toByteArray())

        val exitCode =
            QalipsisTestRunner.withScenarios("subscriber-mqtt-string-deserializer").withEnvironments("scenario")
                .execute()

        Assertions.assertEquals(0, exitCode)
        assertThat(MqttSubscribeScenario.receivedMessages).all {
            hasSize(MqttSubscribeScenario.minions)
            containsOnly("10")
        }
    }

    @Test
    @Timeout(20)
    internal fun `should run the consumer scenario with json deserializer`() {
        MqttSubscribeScenario.receivedMessages.clear()
        val topicName = "test/json"
        client.publish(topicName, JsonMapper().writeValueAsBytes(MqttSubscribeScenario.User("1")))

        val exitCode =
            QalipsisTestRunner.withScenarios("subscriber-mqtt-json-deserializer").withEnvironments("scenario").execute()

        Assertions.assertEquals(0, exitCode)
        assertThat(MqttSubscribeScenario.receivedMessages).all {
            hasSize(MqttSubscribeScenario.minions)
            containsOnly("1")
        }
    }

    @Test
    @Timeout(20)
    internal fun `should run the consumer scenario with byte array deserializer`() {
        MqttSubscribeScenario.receivedMessages.clear()
        val topicName = "test/bytearray"
        client.publish(topicName, "30".toByteArray())

        val exitCode =
            QalipsisTestRunner.withScenarios("subscriber-mqtt-bytearray-deserializer").withEnvironments("scenario")
                .execute()

        Assertions.assertEquals(0, exitCode)
        assertThat(MqttSubscribeScenario.receivedMessages).all {
            hasSize(MqttSubscribeScenario.minions)
            containsOnly("30")
        }
    }

    @Test
    @Timeout(20)
    internal fun `should run the consumer scenario with custom deserializer`() {
        MqttSubscribeScenario.receivedMessages.clear()
        val topicName = "test/custom"
        client.publish(topicName, JsonMapper().writeValueAsBytes(MqttSubscribeScenario.User("4")))

        val exitCode =
            QalipsisTestRunner.withScenarios("subscriber-mqtt-custom-deserializer").withEnvironments("scenario")
                .execute()

        Assertions.assertEquals(0, exitCode)
        assertThat(MqttSubscribeScenario.receivedMessages).all {
            hasSize(MqttSubscribeScenario.minions)
            containsOnly("4")
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
