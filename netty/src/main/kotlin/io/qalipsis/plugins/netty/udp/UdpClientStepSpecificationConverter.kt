package io.qalipsis.plugins.netty.udp

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.udp.spec.UdpClientStepSpecification
import jakarta.inject.Named
import kotlin.coroutines.CoroutineContext

/**
 * [StepSpecificationConverter] from [UdpClientStepSpecification] to [UdpClientStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class UdpClientStepSpecificationConverter(
    private val eventLoopGroupSupplier: EventLoopGroupSupplier,
    private val eventsLogger: EventsLogger,
    private val meterRegistry: MeterRegistry,
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineContext: CoroutineContext
) : StepSpecificationConverter<UdpClientStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is UdpClientStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<UdpClientStepSpecification<*>>) {
        val spec = creationContext.stepSpecification
        val step = UdpClientStep(
            spec.name, spec.retryPolicy,
            ioCoroutineContext,
            spec.requestFactory,
            spec.connectionConfiguration,
            eventLoopGroupSupplier,
            eventsLogger.takeIf { spec.monitoringConfiguration.events },
            meterRegistry.takeIf { spec.monitoringConfiguration.meters }
        )
        creationContext.createdStep(step)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
