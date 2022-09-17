/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

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
