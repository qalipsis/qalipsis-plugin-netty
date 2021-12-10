package io.qalipsis.plugins.netty.mqtt.subscriber.spec

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.messaging.deserializer.MessageDeserializer
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonStepSpecification
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.UnicastSpecification
import io.qalipsis.plugins.netty.NettyPluginSpecification
import io.qalipsis.plugins.netty.NettyScenarioSpecification
import io.qalipsis.plugins.netty.mqtt.spec.MqttAuthentication
import io.qalipsis.plugins.netty.mqtt.spec.MqttConnectionConfiguration
import io.qalipsis.plugins.netty.mqtt.spec.MqttQoS
import io.qalipsis.plugins.netty.mqtt.spec.MqttVersion
import io.qalipsis.plugins.netty.mqtt.subscriber.MqttSubscribeRecord
import io.qalipsis.plugins.netty.mqtt.subscriber.deserializer.MqttByteArrayDeserializer
import java.time.Duration
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Positive
import kotlin.reflect.KClass

interface MqttSubscribeStepSpecification<V : Any> : UnicastSpecification,
    ConfigurableStepSpecification<Unit, MqttSubscribeRecord<V?>, MqttDeserializerSpecification<V>> {

    /**
     * Configures the connection of the MQTT broker, defaults to localhost:1883.
     */
    fun connect(configurationBlock: MqttConnectionConfiguration.() -> Unit)

    /**
     * Configures the monitoring of the subscribe step.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)

    /**
     * Defines the client name in the subscriber client.
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
     * Defines the topic filter for the subscriber to apply.
     */
    fun topicFilter(topic: String)

    /**
     * Defines qos level of the subscription, defaults to 1.
     * See [here](https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#qos-flows).
     */
    fun qoS(subscribeQoS: MqttQoS)

    /**
     * Defines the number of threads to use in the subscriber, defaults to 2.
     */
    fun concurrency(concurrency: Int)
}

interface MqttDeserializerSpecification<V : Any> :
    StepSpecification<Unit, MqttSubscribeRecord<V?>, MqttDeserializerSpecification<V>> {

    /**
     * Uses an instance of [valueDeserializer] to deserialize the native values of MQTT.
     * This class must be an implementation of [MessageDeserializer].
     */
    fun <V1 : Any> deserialize(valueDeserializer: String): StepSpecification<Unit, MqttSubscribeRecord<V1?>, *>

    /**
     * Uses an instance of [valueDeserializer] to deserialize the native values of MQTT.
     * This class must be an implementation of [MessageDeserializer].
     */
    fun <V1 : Any> deserialize(
        valueDeserializer: KClass<out MessageDeserializer<V1>>
    ): StepSpecification<Unit, MqttSubscribeRecord<V1?>, *>

    /**
     * Uses an instance of [valueDeserializer] to deserialize the native values of MQTT.
     * This class must be an implementation of [MessageDeserializer].
     */
    fun <V1 : Any> deserialize(
        valueDeserializer: MessageDeserializer<V1>
    ): StepSpecification<Unit, MqttSubscribeRecord<V1?>, *>
}

/**
 * Specification to a MQTT subscriber, implementation of [MqttSubscribeStepSpecification].
 *
 * @author Gabriel Moraes
 */
@Spec
internal class MqttSubscribeStepSpecificationImpl<V : Any>(deserializer: MessageDeserializer<V>) :
    AbstractStepSpecification<Unit, MqttSubscribeRecord<V?>, MqttDeserializerSpecification<V>>(),
    MqttDeserializerSpecification<V>, MqttSubscribeStepSpecification<V>,
    NettyPluginSpecification<Unit, MqttSubscribeRecord<V?>, MqttDeserializerSpecification<V>>,
    SingletonStepSpecification {

    internal var monitoringConfig = StepMonitoringConfiguration()

    internal val mqttSubscribeConfiguration = MqttSubscribeConfiguration(valueDeserializer = deserializer)

    override val singletonConfiguration: SingletonConfiguration = SingletonConfiguration(SingletonType.UNICAST)

    override fun connect(configurationBlock: MqttConnectionConfiguration.() -> Unit) {
        mqttSubscribeConfiguration.connectionConfiguration.configurationBlock()
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    override fun clientName(clientName: String) {
        mqttSubscribeConfiguration.client = clientName
    }

    override fun auth(authenticationBlock: MqttAuthentication.() -> Unit) {
        mqttSubscribeConfiguration.authentication.authenticationBlock()
    }

    override fun protocol(mqttVersion: MqttVersion) {
        mqttSubscribeConfiguration.protocol = mqttVersion
    }

    override fun topicFilter(topic: String) {
        mqttSubscribeConfiguration.topic = topic
    }

    override fun qoS(subscribeQoS: MqttQoS) {
        mqttSubscribeConfiguration.subscribeQoS = subscribeQoS
    }

    override fun concurrency(concurrency: Int) {
        mqttSubscribeConfiguration.concurrency = concurrency
    }

    override fun forwardOnce(bufferSize: Int, idleTimeout: Duration) {
        singletonConfiguration.bufferSize = bufferSize
        singletonConfiguration.idleTimeout = idleTimeout
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V1 : Any> deserialize(valueDeserializer: String): StepSpecification<Unit, MqttSubscribeRecord<V1?>, *> {
        this as MqttSubscribeStepSpecificationImpl<V1>
        this.mqttSubscribeConfiguration.valueDeserializer =
            (Class.forName(valueDeserializer) as Class<MessageDeserializer<V1>>).getDeclaredConstructor().newInstance()

        return this
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V1 : Any> deserialize(valueDeserializer: KClass<out MessageDeserializer<V1>>): StepSpecification<Unit, MqttSubscribeRecord<V1?>, *> {
        this as MqttSubscribeStepSpecificationImpl<V1>
        this.mqttSubscribeConfiguration.valueDeserializer =
            valueDeserializer.java.getDeclaredConstructor().newInstance()

        return this
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V1 : Any> deserialize(valueDeserializer: MessageDeserializer<V1>): StepSpecification<Unit, MqttSubscribeRecord<V1?>, *> {
        this as MqttSubscribeStepSpecificationImpl<V1>
        this.mqttSubscribeConfiguration.valueDeserializer = valueDeserializer

        return this
    }
}

internal data class MqttSubscribeConfiguration<V>(
    internal val connectionConfiguration: MqttConnectionConfiguration = MqttConnectionConfiguration(),
    @field:NotBlank internal var topic: String = "",
    @field:Positive internal var concurrency: Int = 2,
    internal var subscribeQoS: MqttQoS = MqttQoS.AT_LEAST_ONCE,
    internal val authentication: MqttAuthentication = MqttAuthentication(),
    @field:NotNull internal var protocol: MqttVersion = MqttVersion.MQTT_3_1_1,
    @field:NotBlank internal var client: String = "",
    internal var valueDeserializer: MessageDeserializer<V>,
)

/**
 * Creates a MQTT subscriber to receive pushed data from topics of a MQTT broker and forward each message
 * to the next step.
 *
 * This step is generally used in conjunction with join to assert data or inject them in a workflow.
 *
 * You can learn more on [MQTT website](https://mqtt.org).
 *
 * @author Gabriel Moraes
 */
fun NettyScenarioSpecification.mqttSubscribe(
    configurationBlock: MqttSubscribeStepSpecification<ByteArray>.() -> Unit
): MqttDeserializerSpecification<ByteArray> {
    val step = MqttSubscribeStepSpecificationImpl(MqttByteArrayDeserializer())
    step.configurationBlock()
    (this as StepSpecificationRegistry).add(step)
    return step
}

