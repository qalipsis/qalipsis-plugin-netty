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

package io.qalipsis.plugins.netty.tcp

import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
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
    private val meterRegistry: CampaignMeterRegistry,
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
