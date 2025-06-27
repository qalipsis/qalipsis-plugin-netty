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
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import io.mockk.verifyOrder
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.handler.codec.mqtt.MqttFixedHeader
import io.netty.handler.codec.mqtt.MqttMessageType
import io.netty.handler.codec.mqtt.MqttProperties
import io.netty.handler.codec.mqtt.MqttPublishMessage
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.messaging.deserializer.MessageDeserializer
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Meter
import io.qalipsis.plugins.netty.mqtt.spec.MqttQoS
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.CleanMockkRecordedCalls
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.atomic.AtomicLong

/**
 * @author Gabriel Moraes
 */
@CleanMockkRecordedCalls
internal class MqttSubscribeConverterTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private val valueSerializer: MessageDeserializer<String> = relaxedMockk {
        every { deserialize(any()) } answers { firstArg<ByteArray>().decodeToString() }
    }

    private val recordsCounter = relaxedMockk<Counter>()

    private val valueBytesCounter = relaxedMockk<Counter>()

    private val eventsLogger = relaxedMockk<EventsLogger>()

    @Test
    @Timeout(2)
    fun `should deserialize without monitoring`() = testDispatcherProvider.runTest {
        executeConversion(enableMonitoring = false)

        confirmVerified(recordsCounter, valueBytesCounter, valueSerializer)
    }

    @Test
    @Timeout(2)
    fun `should deserialize and count the values bytes`() = testDispatcherProvider.runTest {
        executeConversion(enableMonitoring = true)

        verifyOrder {
            valueBytesCounter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            valueBytesCounter.increment(7.0)
            valueBytesCounter.increment(8.0)
            valueBytesCounter.increment(8.0)
        }

        confirmVerified(valueBytesCounter, valueSerializer)
    }

    @Test
    @Timeout(2)
    fun `should deserialize and count the records`() = testDispatcherProvider.runTest {
        executeConversion(enableMonitoring = true)

        verifyOrder {
            recordsCounter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            recordsCounter.increment()
            recordsCounter.increment()
            recordsCounter.increment()
        }

        confirmVerified(recordsCounter, valueSerializer)
    }

    private suspend fun executeConversion(enableMonitoring: Boolean? = null) {

        val publishMessage1 = MqttPublishMessage(
            MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.AT_LEAST_ONCE.mqttNativeQoS, true, 0),
            MqttPublishVariableHeader("test", 1),
            Unpooled.buffer().writeBytes("message".toByteArray())
        )
        val publishMessage2 = MqttPublishMessage(
            MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.AT_LEAST_ONCE.mqttNativeQoS, true, 0),
            MqttPublishVariableHeader("test", 2, MqttProperties()),
            Unpooled.buffer().writeBytes("message2".toByteArray())
        )
        val publishMessage3 = MqttPublishMessage(
            MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.AT_LEAST_ONCE.mqttNativeQoS, true, 0),
            MqttPublishVariableHeader("test", 3, MqttProperties()),
            Unpooled.buffer().writeBytes("message3".toByteArray())
        )
        var meterRegistry: CampaignMeterRegistry? = null
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toEventTags() } returns emptyMap()
            every { scenarioName } returns "scenario-name"
            every { stepName } returns "step-name"
        }
        if (enableMonitoring == true) {
            val tags: Map<String, String> = startStopContext.toEventTags()
            meterRegistry = relaxedMockk<CampaignMeterRegistry> {
                every {
                    counter(
                        "scenario-name",
                        "step-name",
                        "netty-mqtt-subscribe-consumed-records",
                        refEq(tags)
                    )
                } returns recordsCounter
                every { recordsCounter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>()) } returns recordsCounter
                every {
                    counter(
                        "scenario-name",
                        "step-name",
                        "netty-mqtt-subscribe-consumed-value-bytes",
                        refEq(tags)
                    )
                } returns valueBytesCounter
                every { valueBytesCounter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>()) } returns valueBytesCounter
            }
        }

        val converter = MqttSubscribeConverter(
            valueSerializer,
            meterRegistry,
            eventsLogger
        )

        converter.start(startStopContext)
        // when
        val channel = Channel<MqttSubscribeRecord<String>>(3)
        converter.supply(
            AtomicLong(120),
            publishMessage1,
            relaxedMockk { coEvery { send(any()) } coAnswers { channel.send(firstArg()) } })
        converter.supply(
            AtomicLong(121),
            publishMessage2,
            relaxedMockk { coEvery { send(any()) } coAnswers { channel.send(firstArg()) } })
        converter.supply(
            AtomicLong(122),
            publishMessage3,
            relaxedMockk { coEvery { send(any()) } coAnswers { channel.send(firstArg()) } })

        // receives messages converted sent in the output channel.
        val results = listOf(channel.receive(), channel.receive(), channel.receive())

        // then
        assertThat(results).all {
            hasSize(3)
            index(0).all {
                prop("value").isNotNull().isEqualTo("message")
                prop("offset").isEqualTo(120L)
                prop("topicName").isEqualTo("test")
                prop("packetId").isEqualTo(1)
                prop("properties").isNotNull()
                prop("consumedTimestamp").isNotNull()
            }
            index(1).all {
                prop("value").isNotNull().isEqualTo("message2")
                prop("offset").isEqualTo(121L)
                prop("topicName").isEqualTo("test")
                prop("packetId").isEqualTo(2)
                prop("properties").isNotNull()
                prop("consumedTimestamp").isNotNull()
            }
            index(2).all {
                prop("value").isNotNull().isEqualTo("message3")
                prop("offset").isEqualTo(122L)
                prop("topicName").isEqualTo("test")
                prop("packetId").isEqualTo(3)
                prop("properties").isNotNull()
                prop("consumedTimestamp").isNotNull()
            }
        }

        verify {
            valueSerializer.deserialize(eq(ByteBufUtil.getBytes(publishMessage1.payload())))
            valueSerializer.deserialize(eq(ByteBufUtil.getBytes(publishMessage2.payload())))
            valueSerializer.deserialize(eq(ByteBufUtil.getBytes(publishMessage3.payload())))
        }
    }
}
