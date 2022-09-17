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

package io.qalipsis.plugins.netty.mqtt

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAll
import assertk.assertions.containsNone
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import io.netty.buffer.ByteBufUtil
import io.qalipsis.api.lang.concurrentList
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.mqtt.spec.MqttAuthentication
import io.qalipsis.plugins.netty.mqtt.spec.MqttConnectionConfiguration
import io.qalipsis.plugins.netty.mqtt.spec.MqttQoS
import io.qalipsis.plugins.netty.mqtt.spec.MqttVersion
import io.qalipsis.test.coroutines.TestDispatcherProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.GenericContainer
import java.util.concurrent.CountDownLatch

internal abstract class AbstractMqttClientIntegrationTest(private val container: GenericContainer<Nothing>) {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private lateinit var client: MqttClient

    private lateinit var connectionConfiguration: MqttConnectionConfiguration

    @BeforeEach
    internal fun setUp() {
        connectionConfiguration =
            MqttConnectionConfiguration(container.host, container.getMappedPort(DEFAULT_MQTT_PORT))
    }

    @AfterEach
    internal fun destroy() {
        kotlin.runCatching {
            client.close()
        }
    }

    @Test
    @Timeout(10)
    internal fun `should consume data from topic using mqtt version 3_1_1`() = testDispatcherProvider.run {
        val clientOptions = MqttClientOptions(
            connectionConfiguration = connectionConfiguration,
            authentication = MqttAuthentication(),
            clientId = "clientName",
            protocolVersion = MqttVersion.MQTT_3_1_1
        )

        client = MqttClient(clientOptions, NativeTransportUtils.getEventLoopGroup())

        val consumedMessages = concurrentList<String>()
        val latch = CountDownLatch(1)

        val subscription = MqttSubscriber("test") { message ->
            consumedMessages.add(ByteBufUtil.getBytes(message.payload()).decodeToString())
            latch.countDown()
        }

        client.subscribe(subscription)

        client.publish("test", "consumed_3_1_1".toByteArray())

        latch.await()

        assertThat(consumedMessages).contains("consumed_3_1_1")
    }

    @Test
    @Timeout(10)
    internal fun `should consume data from topic using mqtt version 3_1`() = testDispatcherProvider.run {
        val clientOptions = MqttClientOptions(
            connectionConfiguration = connectionConfiguration,
            authentication = MqttAuthentication(),
            clientId = "client3",
            protocolVersion = MqttVersion.MQTT_3_1
        )

        client = MqttClient(clientOptions, NativeTransportUtils.getEventLoopGroup())

        val consumedMessages = concurrentList<String>()
        val latch = CountDownLatch(1)

        val subscription = MqttSubscriber("test3") { message ->
            consumedMessages.add(ByteBufUtil.getBytes(message.payload()).decodeToString())
            latch.countDown()
        }
        client.subscribe(subscription)

        client.publish("test3", "consumed_3_1".toByteArray())

        latch.await()

        assertThat(consumedMessages).contains("consumed_3_1")
    }

    @Test
    @Timeout(10)
    internal fun `should consume data from topic using mqtt version 5`() = testDispatcherProvider.run {
        val clientOptions = MqttClientOptions(
            connectionConfiguration = connectionConfiguration,
            authentication = MqttAuthentication(),
            clientId = "client5",
            protocolVersion = MqttVersion.MQTT_5
        )

        client = MqttClient(clientOptions, NativeTransportUtils.getEventLoopGroup())

        val consumedMessages = concurrentList<String>()
        val latch = CountDownLatch(1)
        val subscription = MqttSubscriber("testV5") { message ->
            consumedMessages.add(ByteBufUtil.getBytes(message.payload()).decodeToString())
            latch.countDown()
        }
        client.subscribe(subscription)

        client.publish("testV5", "consumed_5".toByteArray())

        latch.await()

        assertThat(consumedMessages).contains("consumed_5")
    }

    @Test
    @Timeout(10)
    internal fun `should consume data from topic using multi level wildcards`() = testDispatcherProvider.run {
        val clientOptions = MqttClientOptions(
            connectionConfiguration = connectionConfiguration,
            authentication = MqttAuthentication(),
            clientId = "clientMultiLevel",
            protocolVersion = MqttVersion.MQTT_3_1
        )

        client = MqttClient(clientOptions, NativeTransportUtils.getEventLoopGroup())

        val consumedMessages = concurrentList<String>()
        val latch = CountDownLatch(3)
        val subscription = MqttSubscriber("multi/#") { message ->
            consumedMessages.add(ByteBufUtil.getBytes(message.payload()).decodeToString())
            latch.countDown()
        }
        client.subscribe(subscription)

        client.publish("multi/multi/level", "1".toByteArray())
        client.publish("multi/level", "2".toByteArray())
        client.publish("multi/other/level", "3".toByteArray())

        latch.await()

        assertThat(consumedMessages).hasSize(3)
    }

    @Test
    @Timeout(10)
    internal fun `should consume data from topic using single level wildcards`() = testDispatcherProvider.run {
        val clientOptions = MqttClientOptions(
            connectionConfiguration = connectionConfiguration,
            authentication = MqttAuthentication(),
            clientId = "clientMultiLevel",
            protocolVersion = MqttVersion.MQTT_3_1
        )

        client = MqttClient(clientOptions, NativeTransportUtils.getEventLoopGroup())

        val consumedMessages = concurrentList<String>()
        val latch = CountDownLatch(2)
        val subscription = MqttSubscriber("singletopic/+") { message ->
            consumedMessages.add(ByteBufUtil.getBytes(message.payload()).decodeToString())
            latch.countDown()
        }
        client.subscribe(subscription)

        client.publish("singletopic/multi/level", "value1".toByteArray())
        client.publish("singletopic/level", "value3".toByteArray())
        client.publish("singletopic/other", "value4".toByteArray())
        client.publish("singletopic/multi/otherlevel", "value2".toByteArray())

        latch.await()

        assertThat(consumedMessages).all {
            hasSize(2)
            containsNone("value1", "value2")
        }
    }

    @Test
    @Timeout(10)
    internal fun `should consume data only from subscribed topic`() = testDispatcherProvider.run {
        val clientOptions = MqttClientOptions(
            connectionConfiguration = connectionConfiguration,
            authentication = MqttAuthentication(),
            clientId = "clientMultiLevel",
            protocolVersion = MqttVersion.MQTT_3_1
        )

        client = MqttClient(clientOptions, NativeTransportUtils.getEventLoopGroup())

        val consumedMessages = concurrentList<String>()
        val latch = CountDownLatch(2)
        val subscription = MqttSubscriber("onlysubscribed") { message ->
            consumedMessages.add(ByteBufUtil.getBytes(message.payload()).decodeToString())
            latch.countDown()
        }
        client.subscribe(subscription)

        client.publish("onlysubscribed", "1".toByteArray())
        client.publish("notsubscribed", "2".toByteArray())
        client.publish("onlysubscribed", "3".toByteArray())

        latch.await()

        assertThat(consumedMessages).all {
            hasSize(2)
            doesNotContain("2")
        }
    }

    @Test
    @Timeout(10)
    internal fun `should consume data from topic when qos is level 2`() = testDispatcherProvider.run {
        val clientOptions = MqttClientOptions(
            connectionConfiguration = connectionConfiguration,
            authentication = MqttAuthentication(),
            clientId = "qos2",
            protocolVersion = MqttVersion.MQTT_3_1
        )

        client = MqttClient(clientOptions, NativeTransportUtils.getEventLoopGroup())

        val consumedMessages = concurrentList<String>()
        val latch = CountDownLatch(1)
        val subscription = MqttSubscriber("topic/qos/2", qoS = MqttQoS.EXACTLY_ONCE.mqttNativeQoS) { message ->
            consumedMessages.add(ByteBufUtil.getBytes(message.payload()).decodeToString())
            latch.countDown()
        }
        client.subscribe(subscription)

        client.publish("topic/qos/2", "1".toByteArray(), MqttQoS.EXACTLY_ONCE.mqttNativeQoS)

        latch.await()

        assertThat(consumedMessages).all {
            hasSize(1)
            containsAll("1")
        }
    }
}
