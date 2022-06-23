package io.qalipsis.plugins.netty.mqtt.subscriber.spec

import io.micrometer.core.instrument.MeterRegistry
import io.netty.handler.codec.mqtt.MqttPublishMessage
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.messaging.deserializer.MessageDeserializer
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepMonitoringConfiguration
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
    private val meterRegistry: MeterRegistry,
    private val eventsLogger: EventsLogger
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
                subscribeConfiguration.valueDeserializer,
                spec.monitoringConfig
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
        valueDeserializer: MessageDeserializer<*>,
        monitoringConfig: StepMonitoringConfiguration,
    ): DatasourceObjectConverter<MqttPublishMessage, out Any?> {

        return MqttSubscribeConverter(
            valueDeserializer,
            meterRegistry = meterRegistry.takeIf { monitoringConfig.meters },
            eventsLogger = eventsLogger.takeIf { monitoringConfig.events }
        )
    }

}
