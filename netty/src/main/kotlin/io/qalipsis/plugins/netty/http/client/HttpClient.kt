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
