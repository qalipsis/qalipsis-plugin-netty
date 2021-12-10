package io.qalipsis.plugins.netty.mqtt.publisher

/**
 * Records the metrics for the NETTY MQTT publisher.
 *
 * @property recordsCount count of records.
 * @property sentBytes records the number of bytes.
 *
 * @author Alex Averyanov
 */
data class MqttPublishMeters (
    val recordsCount: Int = 0,
    val sentBytes: Int = 0,
)