package io.qalipsis.plugins.netty.mqtt.publisher

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.mqtt.MqttClientOptions
import io.qalipsis.plugins.netty.mqtt.publisher.spec.MqttPublishConfiguration
import io.qalipsis.plugins.netty.mqtt.publisher.spec.MqttPublishStepSpecificationImpl
/**
 * [StepSpecificationConverter] from [MqttPublishStepSpecificationImpl] to [MqttPublishStep].
 *
 * @author Gabriel Moraes
 */
@StepConverter
internal class MqttPublishStepSpecificationConverter(
    private val eventLoopGroupSupplier: EventLoopGroupSupplier,
    private val meterRegistry: MeterRegistry,
    private val eventsLogger: EventsLogger,
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
            eventsLogger = eventsLogger.takeIf { spec.monitoringConfig.events },
            meterRegistry = meterRegistry.takeIf { spec.monitoringConfig.meters },
            mqttClientOptions = buildClientOptions(spec.mqttPublishConfiguration),
            recordsFactory = spec.mqttPublishConfiguration.recordsFactory as suspend (ctx: StepContext<*, *>, input: I) -> List<MqttPublishRecord>
        )

        creationContext.createdStep(step)
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
