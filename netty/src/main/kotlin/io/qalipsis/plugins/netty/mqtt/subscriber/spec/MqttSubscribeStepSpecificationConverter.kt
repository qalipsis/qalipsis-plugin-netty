package io.qalipsis.plugins.netty.mqtt.subscriber.spec

import io.micrometer.core.instrument.MeterRegistry
import io.netty.handler.codec.mqtt.MqttPublishMessage
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.context.StepId
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.messaging.deserializer.MessageDeserializer
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.netty.mqtt.MqttClientOptions
import io.qalipsis.plugins.netty.mqtt.subscriber.MqttSubscribeConverter
import io.qalipsis.plugins.netty.mqtt.subscriber.MqttSubscribeIterativeReader

/**
 * [StepSpecificationConverter] from [MqttSubscribeStepSpecificationImpl] to [MqttSubscribeIterativeReader] for a data
 * source.
 *
 * @author Gabriel Moraes
 */
@StepConverter
internal class MqttSubscribeStepSpecificationConverter(
    private val meterRegistry: MeterRegistry
) : StepSpecificationConverter<MqttSubscribeStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is MqttSubscribeStepSpecificationImpl<*>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<MqttSubscribeStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val subscribeConfiguration = spec.mqttSubscribeConfiguration

        val stepId = spec.name
        val reader = MqttSubscribeIterativeReader(
            buildClientOptions(subscribeConfiguration),
            subscribeConfiguration.concurrency,
            subscribeConfiguration.topic,
            subscribeConfiguration.subscribeQoS
        )

        val step = IterativeDatasourceStep(
            stepId,
            reader,
            NoopDatasourceObjectProcessor(),
            buildConverter(
                stepId,
                subscribeConfiguration.valueDeserializer,
                subscribeConfiguration.metricsConfiguration
            )
        )
        creationContext.createdStep(step)
    }

    private fun buildClientOptions(subscribeConfiguration: MqttSubscribeConfiguration<out Any>): MqttClientOptions {
        return MqttClientOptions(
            connectionConfiguration = subscribeConfiguration.connectionConfiguration,
            authentication = subscribeConfiguration.authentication,
            clientId = subscribeConfiguration.client,
            protocolVersion = subscribeConfiguration.protocol
        )
    }

    private fun buildConverter(
        stepId: StepId,
        valueDeserializer: MessageDeserializer<*>,
        metricsConfiguration: MqttSubscriberMetricsConfiguration
    ): DatasourceObjectConverter<MqttPublishMessage, out Any?> {

        val consumedValueBytesCounter = supplyIf(metricsConfiguration.receivedBytes) {
            meterRegistry.counter("mqtt-subscribe-value-bytes", "step", stepId)
        }

        val consumedRecordsCounter = supplyIf(metricsConfiguration.recordsCount) {
            meterRegistry.counter("mqtt-subscribe-records", "step", stepId)
        }

        return MqttSubscribeConverter(
            valueDeserializer,
            consumedValueBytesCounter,
            consumedRecordsCounter
        )
    }

}