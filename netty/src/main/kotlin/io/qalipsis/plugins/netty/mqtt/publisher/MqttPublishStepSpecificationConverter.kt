/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.plugins.netty.mqtt.publisher

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
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
    private val meterRegistry: CampaignMeterRegistry,
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
