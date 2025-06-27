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

package io.qalipsis.plugins.netty.mqtt.publisher.spec

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.netty.NettyPluginSpecification
import io.qalipsis.plugins.netty.mqtt.publisher.MqttPublishRecord
import io.qalipsis.plugins.netty.mqtt.publisher.MqttPublishResult
import io.qalipsis.plugins.netty.mqtt.spec.MqttAuthentication
import io.qalipsis.plugins.netty.mqtt.spec.MqttConnectionConfiguration
import io.qalipsis.plugins.netty.mqtt.spec.MqttVersion
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

interface MqttPublishStepSpecification<I> :
    NettyPluginSpecification<I, MqttPublishResult<I>, MqttPublishStepSpecification<I>>,
    ConfigurableStepSpecification<I, MqttPublishResult<I>, MqttPublishStepSpecification<I>> {

    /**
     * Configures the connection of the MQTT broker, defaults to localhost:1883.
     */
    fun connect(configurationBlock: MqttConnectionConfiguration.() -> Unit)

    /**
     * Configures the monitoring of the publish step..
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)

    /**
     * Defines the client name in the publisher client.
     */
    fun clientName(clientName: String)

    /**
     * Configures the authentication used to establish a connection with a broker, defaults to no authentication.
     */
    fun auth(authenticationBlock: MqttAuthentication.() -> Unit)

    /**
     * Defines the MQTT protocol version, defaults to MQTT_3_1_1.
     */
    fun protocol(mqttVersion: MqttVersion)

    /**
     * Defines the records to be published, it receives the context and the output from previous step that can be used
     * when defining the records.
     */
    fun records(recordsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<MqttPublishRecord>)
}

/**
 * Specification to a MQTT publisher, implementation of [MqttPublishStepSpecification].
 *
 * @author Gabriel Moraes
 */
@Spec
internal class MqttPublishStepSpecificationImpl<I> : AbstractStepSpecification<I, MqttPublishResult<I>, MqttPublishStepSpecification<I>>(),
    MqttPublishStepSpecification<I>, NettyPluginSpecification<I, MqttPublishResult<I>, MqttPublishStepSpecification<I>> {

    internal var monitoringConfig = StepMonitoringConfiguration()

    internal val mqttPublishConfiguration = MqttPublishConfiguration<I>()

    override fun connect(configurationBlock: MqttConnectionConfiguration.() -> Unit) {
        mqttPublishConfiguration.connectionConfiguration.configurationBlock()
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    override fun clientName(clientName: String) {
        mqttPublishConfiguration.client = clientName
    }

    override fun auth(authenticationBlock: MqttAuthentication.() -> Unit) {
        mqttPublishConfiguration.authentication.authenticationBlock()
    }

    override fun protocol(mqttVersion: MqttVersion) {
        mqttPublishConfiguration.protocol = mqttVersion
    }

    override fun records(recordsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<MqttPublishRecord>) {
        mqttPublishConfiguration.recordsFactory = recordsFactory
    }
}

internal data class MqttPublishConfiguration<I>(
    internal val connectionConfiguration: MqttConnectionConfiguration = MqttConnectionConfiguration(),
    internal val authentication: MqttAuthentication = MqttAuthentication(),
    @field:NotNull internal var protocol: MqttVersion = MqttVersion.MQTT_3_1_1,
    @field:NotBlank internal var client: String = "",
    internal var recordsFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<MqttPublishRecord>) =
        { _, _ -> emptyList() }
)

/**
 * Creates a step to push data onto topics of a MQTT broker and forwards the input to the next step.
 *
 * You can learn more on [MQTT website](https://mqtt.org).
 *
 * @author Gabriel Moraes
 */
fun <I> NettyPluginSpecification<*, I, *>.mqttPublish(
    configurationBlock: MqttPublishStepSpecification<I>.() -> Unit
): MqttPublishStepSpecification<I> {
    val step = MqttPublishStepSpecificationImpl<I>()
    step.configurationBlock()
    this.add(step)
    return step
}

