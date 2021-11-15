package io.qalipsis.plugins.netty.tcp

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.tcp.spec.TcpClientStepSpecificationImpl
import jakarta.inject.Named
import kotlin.coroutines.CoroutineContext

/**
 * [StepSpecificationConverter] from [TcpClientStepSpecificationImpl] to [SimpleTcpClientStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class TcpClientStepSpecificationConverter(
    private val eventLoopGroupSupplier: EventLoopGroupSupplier,
    private val eventsLogger: EventsLogger,
    private val meterRegistry: MeterRegistry,
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineContext: CoroutineContext
) : StepSpecificationConverter<TcpClientStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return (stepSpecification is TcpClientStepSpecificationImpl)
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<TcpClientStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val step = spec.poolConfiguration?.let { poolConfiguration ->
            creationContext.stepSpecification.connectionConfiguration.keepConnectionAlive = true
            PooledTcpClientStep(
                spec.name,
                spec.retryPolicy,
                ioCoroutineContext,
                spec.requestFactory,
                spec.connectionConfiguration,
                poolConfiguration,
                eventLoopGroupSupplier,
                eventsLogger.takeIf { spec.monitoringConfiguration.events },
                meterRegistry.takeIf { spec.monitoringConfiguration.meters }
            )
        } ?: SimpleTcpClientStep(
            spec.name,
            spec.retryPolicy,
            ioCoroutineContext,
            spec.requestFactory,
            spec.connectionConfiguration,
            eventLoopGroupSupplier,
            eventsLogger.takeIf { spec.monitoringConfiguration.events },
            meterRegistry.takeIf { spec.monitoringConfiguration.meters }
        )

        creationContext.createdStep(step)
    }

}
