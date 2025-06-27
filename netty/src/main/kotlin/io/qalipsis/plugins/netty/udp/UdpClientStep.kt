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

import io.netty.channel.EventLoopGroup
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.configuration.ConnectionConfiguration

/**
 * Step to send and receive data using UDP.
 *
 * @author Eric Jessé
 */
internal class UdpClientStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val requestFactory: suspend (StepContext<*, *>, I) -> ByteArray,
    private val connectionConfiguration: ConnectionConfiguration,
    private val eventLoopGroupSupplier: EventLoopGroupSupplier,
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: CampaignMeterRegistry?
) : AbstractStep<I, UdpResult<I, ByteArray>>(id, retryPolicy) {

    private lateinit var workerGroup: EventLoopGroup

    override suspend fun start(context: StepStartStopContext) {
        super.start(context)
        workerGroup = eventLoopGroupSupplier.getGroup()
    }

    override suspend fun stop(context: StepStartStopContext) {
        super.stop(context)
        workerGroup.shutdownGracefully()
    }

    override suspend fun execute(context: StepContext<I, UdpResult<I, ByteArray>>) {
        val monitoringCollector = UdpMonitoringCollector(context, eventsLogger, meterRegistry, "udp")
        val input = context.receive()

        val client = UdpClient()
        client.open(connectionConfiguration, workerGroup, monitoringCollector)

        val udpResult = try {
            val request = requestFactory(context, input)
            val response = client.execute(context, request, monitoringCollector)
            monitoringCollector.toResult(input, response, null)
        } catch (e: Exception) {
            // The exception is only considered if not already set in the context.
            throw UdpException(
                monitoringCollector.toResult(input, null, e.takeUnless { monitoringCollector.cause === e })
            )
        }

        if (udpResult.isFailure) {
            throw UdpException(udpResult)
        }

        context.send(udpResult)
    }

}
