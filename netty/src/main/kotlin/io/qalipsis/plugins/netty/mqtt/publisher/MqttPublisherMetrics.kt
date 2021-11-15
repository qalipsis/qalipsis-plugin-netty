package io.qalipsis.plugins.netty.mqtt.publisher

import io.micrometer.core.instrument.Counter

/**
 * Record the metrics of the publisher.
 *
 * @property recordsCount counts the number of records.
 * @property sentBytes records the number of bytes sent.
 *
 * @author Gabriel Moraes
 */
internal data class MqttPublisherMetrics(
    var recordsCount: Counter? = null,
    var sentBytes: Counter? = null
)
