package io.qalipsis.plugins.netty.tcp

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepDecorator
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.plugins.netty.tcp.spec.QueryTcpClientStepSpecification
import jakarta.inject.Named
import kotlin.coroutines.CoroutineContext

/**
 * [StepSpecificationConverter] from [QueryTcpClientStepSpecification] to [QueryTcpClientStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class QueryTcpClientStepSpecificationConverter(
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineContext: CoroutineContext,
    private val eventsLogger: EventsLogger,
    private val meterRegistry: MeterRegistry
) :
    StepSpecificationConverter<QueryTcpClientStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return (stepSpecification is QueryTcpClientStepSpecification)
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<QueryTcpClientStepSpecification<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as QueryTcpClientStepSpecification<I>
        // Validate that the referenced step exists and is a TCP step.
        val referencedStep = creationContext.directedAcyclicGraph.findStep(spec.stepName)?.first
        val connectionOwner = findTcpClientStep(referencedStep) ?: throw InvalidSpecificationException(
            "Step with specified name ${spec.stepName} does not exist or is not a TCP step: $referencedStep"
        )

        val usages = spec.iterations.coerceAtLeast(1).toInt()
        connectionOwner.addUsage(usages)

        val step =
            QueryTcpClientStep(
                spec.name,
                spec.retryPolicy,
                ioCoroutineContext,
                connectionOwner,
                spec.requestFactory,
                eventsLogger.takeIf { spec.monitoringConfiguration.events },
                meterRegistry.takeIf { spec.monitoringConfiguration.meters }
            )
        creationContext.createdStep(step)
    }

    /**
     * Searches the referenced [SimpleTcpClientStep] if any.
     */
    private fun findTcpClientStep(foundStep: Step<*, *>?): TcpClientStep<*, *>? {
        return when (foundStep) {
            is QueryTcpClientStep<*> -> {
                // We can chain the names from step to step.
                foundStep.connectionOwner as TcpClientStep<*, *>
            }
            is TcpClientStep<*, *> -> {
                foundStep
            }
            is StepDecorator<*, *> -> {
                findTcpClientStep(foundStep.decorated)
            }
            else -> null
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
