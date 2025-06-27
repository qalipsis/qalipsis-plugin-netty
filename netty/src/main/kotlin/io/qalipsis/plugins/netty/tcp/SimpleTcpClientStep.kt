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

import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.plugins.netty.ByteArrayRequestBuilder
import io.qalipsis.plugins.netty.ByteArrayRequestBuilderImpl
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.SimpleSocketClientStep
import io.qalipsis.plugins.netty.tcp.client.TcpClient
import io.qalipsis.plugins.netty.tcp.spec.TcpClientConfiguration

/**
 * Step to send and receive data using TCP, using a per-minion connection strategy.
 *
 * @author Eric Jessé
 */
internal class SimpleTcpClientStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    requestFactory: suspend ByteArrayRequestBuilder.(StepContext<*, *>, I) -> ByteArray,
    private val clientConfiguration: TcpClientConfiguration,
    eventLoopGroupSupplier: EventLoopGroupSupplier,
    eventsLogger: EventsLogger?,
    meterRegistry: CampaignMeterRegistry?
) : SimpleSocketClientStep<I, ByteArray, TcpClientConfiguration, ByteArray, ByteArray, ByteArrayRequestBuilder, TcpClient>(
    id,
    retryPolicy,
    ByteArrayRequestBuilderImpl,
    requestFactory,
    clientConfiguration,
    "tcp",
    eventLoopGroupSupplier,
    eventsLogger,
    meterRegistry
), TcpClientStep<I, ConnectionAndRequestResult<I, ByteArray>> {

    public override suspend fun createClient(
        minionId: MinionId,
        monitoringCollector: StepContextBasedSocketMonitoringCollector
    ): TcpClient {
        log.trace { "Creating the TCP client for minion $minionId" }
        val numberOfPlannedUsages =
            if (clientConfiguration.keepConnectionAlive) Long.MAX_VALUE else usagesCount.get().toLong()
        val cli = TcpClient(numberOfPlannedUsages) {
            // When closing, remove the client and the usage counter from the cache.
            clients.remove(minionId)?.close()
            clientsInUse.remove(minionId)
        }
        cli.open(clientConfiguration, workerGroup, monitoringCollector)
        return cli
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }

}

