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
            spec.requestFactory,
            spec.connectionConfiguration,
            eventLoopGroupSupplier,
            eventsLogger.takeIf { spec.monitoringConfiguration.events },
            meterRegistry.takeIf { spec.monitoringConfiguration.meters }
        )

        creationContext.createdStep(step)
    }

}
