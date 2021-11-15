package io.qalipsis.plugins.netty.mqtt.publisher.spec

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.plugins.netty.NettyPluginSpecification
import io.qalipsis.plugins.netty.mqtt.publisher.MqttPublishRecord
import io.qalipsis.plugins.netty.mqtt.spec.MqttAuthentication
import io.qalipsis.plugins.netty.mqtt.spec.MqttConnectionConfiguration
import io.qalipsis.plugins.netty.mqtt.spec.MqttVersion
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

interface MqttPublishStepSpecification<I> :
    NettyPluginSpecification<I, I, MqttPublishStepSpecification<I>>,
    ConfigurableStepSpecification<I, I, MqttPublishStepSpecification<I>> {

    /**
     * Configures the connection of the MQTT broker, defaults to localhost:1883.
     */
    fun connect(configurationBlock: MqttConnectionConfiguration.() -> Unit)

    /**
     * Configures the metrics to apply, defaults to none.
     */
    fun metrics(configurationBlock: MqttPublisherMetricsConfiguration.() -> Unit)

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
internal class MqttPublishStepSpecificationImpl<I> : AbstractStepSpecification<I, I, MqttPublishStepSpecification<I>>(),
    MqttPublishStepSpecification<I>, NettyPluginSpecification<I, I, MqttPublishStepSpecification<I>> {

    internal val mqttPublishConfiguration = MqttPublishConfiguration<I>()

    override fun connect(configurationBlock: MqttConnectionConfiguration.() -> Unit) {
        mqttPublishConfiguration.connectionConfiguration.configurationBlock()
    }

    override fun metrics(configurationBlock: MqttPublisherMetricsConfiguration.() -> Unit) {
        mqttPublishConfiguration.metricsConfiguration.configurationBlock()
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
    internal val metricsConfiguration: MqttPublisherMetricsConfiguration = MqttPublisherMetricsConfiguration(),
    internal val authentication: MqttAuthentication = MqttAuthentication(),
    @field:NotNull internal var protocol: MqttVersion = MqttVersion.MQTT_3_1_1,
    @field:NotBlank internal var client: String = "",
    internal var recordsFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<MqttPublishRecord>) =
        { _, _ -> emptyList() }
)

/**
 * Configuration of the metrics to record for the MQTT publisher.
 *
 * @property recordsCount when true, records the number of published messages.
 *
 * @author Gabriel Moraes
 */
data class MqttPublisherMetricsConfiguration(
    var recordsCount: Boolean = false,
    var sentBytes: Boolean = false
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

