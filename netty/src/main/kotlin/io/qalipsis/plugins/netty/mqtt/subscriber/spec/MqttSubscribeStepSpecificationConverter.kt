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
