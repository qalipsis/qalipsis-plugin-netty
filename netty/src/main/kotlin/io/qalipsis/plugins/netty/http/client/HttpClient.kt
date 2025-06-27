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

package io.qalipsis.plugins.netty.http.client

import io.netty.channel.EventLoopGroup
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpScheme
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.plugins.netty.http.HttpPipelineNames.CHANNEL_MONITORING_HANDLER
import io.qalipsis.plugins.netty.http.HttpPipelineNames.CHUNKED_REQUEST_HANDLER
import io.qalipsis.plugins.netty.http.HttpPipelineNames.INBOUND_HANDLER
import io.qalipsis.plugins.netty.http.http1.Http1ChannelInitializer
import io.qalipsis.plugins.netty.http.http2.Http2ChannelInitializer
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.http.spec.HttpVersion
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.RequestWriter
import io.qalipsis.plugins.netty.socket.SocketClient
import io.qalipsis.plugins.netty.socket.SocketMonitoringCollector
import java.nio.channels.ClosedChannelException

/**
 * Netty long-live HTTP client, that remains open until it is manually closed.
 *
 * @author Eric Jessé
 */
internal class HttpClient(
    plannedUsages: Long = 1,
    onClose: HttpClient.() -> Unit = {}
) : SocketClient<HttpClientConfiguration, io.qalipsis.plugins.netty.http.request.HttpRequest<*>, HttpResponse, HttpClient>(
    plannedUsages,
    onClose
) {

    private lateinit var clientConfiguration: HttpClientConfiguration

    private lateinit var scheme: HttpScheme

    private lateinit var channelInitializer: HttpChannelInitializer

    override suspend fun open(
        clientConfiguration: HttpClientConfiguration,
        workerGroup: EventLoopGroup,
        monitoringCollector: SocketMonitoringCollector
    ) {
        log.trace { "Opening the HTTP client" }
        this.clientConfiguration = clientConfiguration
        this.scheme = if (clientConfiguration.isSecure) HttpScheme.HTTPS else HttpScheme.HTTP
        // A count latch is used to ensure that the TLS handshake is also performed before the step can be used.
        val connectionReadyLatch = SuspendedCountLatch(1)
        channelInitializer = when (clientConfiguration.version) {
            HttpVersion.HTTP_1_1 -> {
                Http1ChannelInitializer(
                    clientConfiguration,
                    monitoringCollector,
                    connectionReadyLatch
                )
            }
            HttpVersion.HTTP_2_0 -> {
                // Disables the Nagle algorithm.
                clientConfiguration.noDelay = false

                Http2ChannelInitializer(
                    clientConfiguration,
                    monitoringCollector,
                    connectionReadyLatch
                )
            }
        }
        open(clientConfiguration, workerGroup, monitoringCollector, channelInitializer, connectionReadyLatch)
        log.trace { "HTTP client is now open" }
    }

    override suspend fun <I> execute(
        stepContext: StepContext<I, *>,
        request: io.qalipsis.plugins.netty.http.request.HttpRequest<*>,
        monitoringCollector: StepContextBasedSocketMonitoringCollector
    ): HttpResponse {
        val responseSlot = ImmutableSlot<Result<HttpResponse>>()
        return try {
            if (!channel.isOpen) {
                throw ClosedChannelException().also {
                    monitoringCollector.recordSentDataFailure(it)
                }
            }

            val requestWriter = channelInitializer.requestExecutionConfigurer
                .configure(request, monitoringCollector, responseSlot)

            val response = internalExecute(
                stepContext,
                requestWriter,
                monitoringCollector,
                responseSlot
            )

            log.trace { "Received response $response" }
            response
        } finally {
            log.trace { "Removing local handlers" }
            removeHandler(channel.pipeline(), CHANNEL_MONITORING_HANDLER)
            removeHandler(channel.pipeline(), INBOUND_HANDLER)
            removeHandler(channel.pipeline(), CHUNKED_REQUEST_HANDLER)
        }
    }

    override suspend fun write(request: Any) {
        (request as RequestWriter).write(channel)
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
