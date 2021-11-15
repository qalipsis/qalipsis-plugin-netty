package io.qalipsis.plugins.netty.mqtt.subscriber.deserializer

import io.qalipsis.api.messaging.deserializer.MessageDeserializer

/**
 * Implementation of [MessageDeserializer] used to send byteArray body.
 */
class MqttByteArrayDeserializer: MessageDeserializer<ByteArray> {

    /**
     * Returns the [body].
     */
    override fun deserialize(body: ByteArray) = body
}