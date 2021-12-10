package io.qalipsis.plugins.netty.mqtt.subscriber

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
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
import io.qalipsis.plugins.netty.mqtt.spec.MqttQoS
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.CleanMockkRecordedCalls
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyExactly
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicLong

/**
 * @author Gabriel Moraes
 */
@CleanMockkRecordedCalls
internal class MqttSubscribeConverterTest{

    private val valueSerializer: MessageDeserializer<String> = relaxedMockk {
        every { deserialize(any()) } answers { firstArg<ByteArray>().decodeToString() }
    }

    private val recordsCounter: Counter = relaxedMockk {}

    private val valueBytesCounter: Counter = relaxedMockk {}

    private val eventsLogger = relaxedMockk<EventsLogger>()

    @Test
    @Timeout(2)
    fun `should deserialize without monitoring`() = runBlockingTest {
        executeConversion(enableMonitoring = false)

        confirmVerified(recordsCounter, valueBytesCounter, valueSerializer)
    }

    @Test
    @Timeout(2)
    fun `should deserialize and count the values bytes`() = runBlockingTest {
        executeConversion(consumedValueBytesCounter = valueBytesCounter, enableMonitoring = true)

        verify {
            valueBytesCounter.increment(7.0)
            valueBytesCounter.increment(8.0)
            valueBytesCounter.increment(8.0)
        }

        confirmVerified(valueBytesCounter, valueSerializer)
    }

    @Test
    @Timeout(2)
    fun `should deserialize and count the records`() = runBlockingTest {
        executeConversion(consumedRecordsCounter = recordsCounter, enableMonitoring = true)

        verifyExactly(3) {
            recordsCounter.increment()
        }

        confirmVerified(recordsCounter, valueSerializer)
    }

    private suspend fun executeConversion(
        consumedValueBytesCounter: Counter? = null,
        consumedRecordsCounter: Counter? = null,
        enableMonitoring: Boolean? = null
    ) {

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

        val metersTags = relaxedMockk<Tags>()
        var meterRegistry: MeterRegistry? = null
        if(enableMonitoring == true) {
            meterRegistry = relaxedMockk<MeterRegistry> {
                every { counter("mqtt-subscribe-consumed-records", refEq(metersTags)) } returns recordsCounter
                every { counter("mqtt-subscribe-consumed-value-bytes", refEq(metersTags)) } returns valueBytesCounter
            }
        }
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns metersTags
        }

        val converter = MqttSubscribeConverter(
            valueSerializer,
            meterRegistry,
            eventsLogger
        )

        converter.start(startStopContext)
        // when
        val channel = Channel<MqttSubscribeRecord<String>>(3)
        converter.supply(AtomicLong(120), publishMessage1, relaxedMockk { coEvery { send(any()) } coAnswers { channel.send(firstArg()) } })
        converter.supply(AtomicLong(121), publishMessage2, relaxedMockk { coEvery { send(any()) } coAnswers { channel.send(firstArg()) } })
        converter.supply(AtomicLong(122), publishMessage3, relaxedMockk { coEvery { send(any()) } coAnswers { channel.send(firstArg()) } })

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
