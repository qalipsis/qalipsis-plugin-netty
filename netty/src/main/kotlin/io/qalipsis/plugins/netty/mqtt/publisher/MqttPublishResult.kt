package io.qalipsis.plugins.netty.mqtt.publisher

/**
 * Records the metrics for the JMS consumer.
 *
 * @property input are the records that can from the previous step.
 * @property meters received records
 * @property records list of MqttPublishRecord
 *
 * @author Alex Averyanov
 */
class MqttPublishResult<I> (
    val input: I,
    val meters: MqttPublishMeters,
    val records: List<MqttPublishRecord>
)