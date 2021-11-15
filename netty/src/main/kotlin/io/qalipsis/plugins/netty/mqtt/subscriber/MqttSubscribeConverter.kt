package io.qalipsis.plugins.netty.mqtt.subscriber

import io.micrometer.core.instrument.Counter
import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.mqtt.MqttPublishMessage
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.messaging.deserializer.MessageDeserializer
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import kotlinx.coroutines.channels.SendChannel
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [DatasourceObjectConverter], that reads a message of native MQTT records and forwards each of
 * them converted as [MqttSubscribeRecord].
 *
 * @author Gabriel Moraes
 */
internal class MqttSubscribeConverter<V>(
    private val valueDeserializer: MessageDeserializer<V>,
    private val consumedValueBytesCounter: Counter?,
    private val consumedRecordsCounter: Counter?
) : DatasourceObjectConverter<MqttPublishMessage, MqttSubscribeRecord<V>> {

    override suspend fun supply(
        offset: AtomicLong, value: MqttPublishMessage,
        output: StepOutput<MqttSubscribeRecord<V>>
    ) {
        val payload = ByteBufUtil.getBytes(value.payload())
        consumedValueBytesCounter?.increment(payload.size.toDouble())
        consumedRecordsCounter?.increment()

        tryAndLogOrNull(log) {
            output.send(
                MqttSubscribeRecord(
                    offset.getAndIncrement(),
                    valueDeserializer.deserialize(payload),
                    value.variableHeader()
                )
            )
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
