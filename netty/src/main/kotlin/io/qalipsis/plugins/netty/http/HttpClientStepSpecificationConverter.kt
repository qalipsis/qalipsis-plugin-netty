package io.qalipsis.plugins.netty.http

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.http.response.HttpBodyDeserializer
import io.qalipsis.plugins.netty.http.response.ResponseConverter
import io.qalipsis.plugins.netty.http.spec.HttpClientStepSpecificationImpl
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

/**
 * [StepSpecificationConverter] from [HttpClientStepSpecificationImpl] to [SimpleHttpClientStep].
 *
 * @author Eric Jessé
 */
@StepConverter
@Suppress("UNCHECKED_CAST")
internal class HttpClientStepSpecificationConverter(
    deserializers: Collection<HttpBodyDeserializer>,
    private val eventLoopGroupSupplier: EventLoopGroupSupplier,
    private val eventsLogger: EventsLogger,
    private val meterRegistry: MeterRegistry,
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineScope: CoroutineScope,
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineContext: CoroutineContext
) : StepSpecificationConverter<HttpClientStepSpecificationImpl<*, *>> {

    private val sortedDeserializers = deserializers.sortedBy(HttpBodyDeserializer::order)

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return (stepSpecification is HttpClientStepSpecificationImpl<*, *>)
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<HttpClientStepSpecificationImpl<*, *>>) {
        val spec = creationContext.stepSpecification as HttpClientStepSpecificationImpl<I, O>
        val step = spec.poolConfiguration?.let { poolConfiguration ->
            creationContext.stepSpecification.connectionConfiguration.keepConnectionAlive = true
            PooledHttpClientStep(
                spec.name,
                spec.retryPolicy,
                ioCoroutineContext,
                ioCoroutineScope,
                spec.requestFactory,
                spec.connectionConfiguration,
                poolConfiguration,
                eventLoopGroupSupplier,
                ResponseConverter(spec.bodyType, sortedDeserializers),
                eventsLogger.takeIf { spec.monitoringConfiguration.events },
                meterRegistry.takeIf { spec.monitoringConfiguration.meters }
            )
        } ?: SimpleHttpClientStep<I, O>(
            spec.name,
            spec.retryPolicy,
            ioCoroutineScope,
            ioCoroutineContext,
            spec.requestFactory,
            spec.connectionConfiguration,
            eventLoopGroupSupplier,
            ResponseConverter(spec.bodyType, sortedDeserializers),
            eventsLogger.takeIf { spec.monitoringConfiguration.events },
            meterRegistry.takeIf { spec.monitoringConfiguration.meters }
        )

        creationContext.createdStep(step)
    }

}
