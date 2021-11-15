package io.qalipsis.plugins.netty.mqtt.publisher

import io.aerisconsulting.catadioptre.KTestable
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.mqtt.MqttClientOptions
import io.qalipsis.plugins.netty.mqtt.publisher.spec.MqttPublishConfiguration
import io.qalipsis.plugins.netty.mqtt.publisher.spec.MqttPublishStepSpecificationImpl
import io.qalipsis.plugins.netty.mqtt.publisher.spec.MqttPublisherMetricsConfiguration

/**
 * [StepSpecificationConverter] from [MqttPublishStepSpecificationImpl] to [MqttPublishStep].
 *
 * @author Gabriel Moraes
 */
@StepConverter
internal class MqttPublishStepSpecificationConverter(
    private val eventLoopGroupSupplier: EventLoopGroupSupplier,
    private val meterRegistry: MeterRegistry
) : StepSpecificationConverter<MqttPublishStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is MqttPublishStepSpecificationImpl<*>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<MqttPublishStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification

        val stepId = spec.name

        @Suppress("UNCHECKED_CAST")
        val step = MqttPublishStep(
            id = stepId,
            retryPolicy = spec.retryPolicy,
            eventLoopGroupSupplier = eventLoopGroupSupplier,
            metrics = buildMetrics(spec.mqttPublishConfiguration.metricsConfiguration, stepId),
            mqttClientOptions = buildClientOptions(spec.mqttPublishConfiguration),
            recordsFactory = spec.mqttPublishConfiguration.recordsFactory as suspend (ctx: StepContext<*, *>, input: I) -> List<MqttPublishRecord>
        )

        creationContext.createdStep(step)
    }

    @KTestable
    private fun buildMetrics(
        metricsConfiguration: MqttPublisherMetricsConfiguration,
        stepId: StepId
    ): MqttPublisherMetrics {
        val publishedValueBytesCounter = supplyIf(metricsConfiguration.sentBytes) {
            meterRegistry.counter("mqtt-publish-value-bytes", "step", stepId)
        }

        val publishedRecordsCounter = supplyIf(metricsConfiguration.recordsCount) {
            meterRegistry.counter("mqtt-publish-records", "step", stepId)
        }

        return MqttPublisherMetrics(publishedRecordsCounter, publishedValueBytesCounter)
    }

    private fun buildClientOptions(subscribeConfiguration: MqttPublishConfiguration<*>): MqttClientOptions {
        return MqttClientOptions(
            connectionConfiguration = subscribeConfiguration.connectionConfiguration,
            authentication = subscribeConfiguration.authentication,
            clientId = subscribeConfiguration.client,
            protocolVersion = subscribeConfiguration.protocol
        )
    }

}
