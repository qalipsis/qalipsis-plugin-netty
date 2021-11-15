package io.qalipsis.plugins.netty.mqtt.publisher

import io.netty.channel.EventLoopGroup
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.mqtt.MqttClient
import io.qalipsis.plugins.netty.mqtt.MqttClientOptions


/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to publish a message into MQTT broker.
 *
 * @property id id of the step.
 * @property retryPolicy of the step.
 * @property metrics for the publish step.
 * @property mqttClientOptions client options to connect to the MQTT broker.
 * @property recordsFactory closure to generate the records to publish.
 *
 * @author Gabriel Moraes
 */
internal class MqttPublishStep<I>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    private val eventLoopGroupSupplier: EventLoopGroupSupplier,
    private val metrics: MqttPublisherMetrics,
    private val mqttClientOptions: MqttClientOptions,
    private val recordsFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<MqttPublishRecord>),
) : AbstractStep<I, I>(id, retryPolicy) {

    private lateinit var mqttClient: MqttClient

    private lateinit var eventLoopGroup: EventLoopGroup

    override suspend fun start(context: StepStartStopContext) {
        eventLoopGroup = eventLoopGroupSupplier.getGroup()
        mqttClient = MqttClient(mqttClientOptions, eventLoopGroup)
    }

    override suspend fun execute(context: StepContext<I, I>) {
        val input = context.receive()

        val recordsToSend = recordsFactory(context, input)

        recordsToSend.forEach {

            val payload = it.value.toByteArray()
            metrics.recordsCount?.increment()
            metrics.sentBytes?.increment(payload.size.toDouble())

            mqttClient.publish(it.topicName, payload, it.retainedMessage, it.qoS.mqttNativeQoS)
        }

        context.send(input)
    }

    override suspend fun stop(context: StepStartStopContext) {
        mqttClient.close()
        eventLoopGroup.shutdownGracefully()
    }

}
