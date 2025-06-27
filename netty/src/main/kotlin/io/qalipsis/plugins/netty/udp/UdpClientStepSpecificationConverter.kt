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

package io.qalipsis.plugins.netty.udp

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.udp.spec.UdpClientStepSpecification

/**
 * [StepSpecificationConverter] from [UdpClientStepSpecification] to [UdpClientStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class UdpClientStepSpecificationConverter(
    private val eventLoopGroupSupplier: EventLoopGroupSupplier,
    private val eventsLogger: EventsLogger,
    private val meterRegistry: CampaignMeterRegistry,
) : StepSpecificationConverter<UdpClientStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is UdpClientStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<UdpClientStepSpecification<*>>) {
        val spec = creationContext.stepSpecification
        val step = UdpClientStep(
            spec.name, spec.retryPolicy,
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
