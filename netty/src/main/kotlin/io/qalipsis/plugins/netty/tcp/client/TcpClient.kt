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

package io.qalipsis.plugins.netty.tcp.client

import io.netty.channel.EventLoopGroup
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.plugins.netty.handlers.ByteArrayInboundHandler
import io.qalipsis.plugins.netty.handlers.monitoring.ChannelMonitoringHandler
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.SocketClient
import io.qalipsis.plugins.netty.socket.SocketMonitoringCollector
import io.qalipsis.plugins.netty.tcp.spec.TcpClientConfiguration

/**
 * Netty long-live TCP client, that remains open until it is manually closed.
 *
 * @author Eric Jessé
 */
internal class TcpClient(
    plannedUsages: Long = 1,
    onClose: TcpClient.() -> Unit = {}
) : SocketClient<TcpClientConfiguration, ByteArray, ByteArray, TcpClient>(plannedUsages, onClose) {

    override suspend fun open(
        clientConfiguration: TcpClientConfiguration,
        workerGroup: EventLoopGroup,
        monitoringCollector: SocketMonitoringCollector
    ) {
        log.trace { "Opening the TCP client" }
        // A count latch is used to ensure that the TLS handshake is als performed before the step is used.
        val connectionReadyLatch = SuspendedCountLatch(1)
        open(
            clientConfiguration,
            workerGroup,
            monitoringCollector,
            TcpChannelInitializer(clientConfiguration, monitoringCollector, connectionReadyLatch),
            connectionReadyLatch
        )
        log.trace { "TCP client is now open" }
    }

    override suspend fun <I> execute(
        stepContext: StepContext<I, *>,
        request: ByteArray,
        monitoringCollector: StepContextBasedSocketMonitoringCollector
    ): ByteArray {
        val responseSlot = ImmutableSlot<Result<ByteArray>>()
        return try {
            channel.pipeline()
                .addLast(CHANNEL_MONITORING_HANDLER, ChannelMonitoringHandler(monitoringCollector))
            channel.pipeline().addLast(INBOUND_HANDLER, ByteArrayInboundHandler(responseSlot))
            internalExecute(stepContext, request, monitoringCollector, responseSlot)
        } finally {
            removeHandler(channel.pipeline(), CHANNEL_MONITORING_HANDLER)
            removeHandler(channel.pipeline(), INBOUND_HANDLER)
        }
    }

    companion object {

        private const val CHANNEL_MONITORING_HANDLER = "channel-monitoring"

        private const val INBOUND_HANDLER = "inbound-handler"

        @JvmStatic
        private val log = logger()
    }
}
