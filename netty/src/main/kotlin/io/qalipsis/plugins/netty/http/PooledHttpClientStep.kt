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

package io.qalipsis.plugins.netty.http

import io.micrometer.core.instrument.MeterRegistry
import io.netty.channel.EventLoopGroup
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.util.ReferenceCounted
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.pool.FixedPool
import io.qalipsis.api.pool.Pool
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.http.client.HttpClient
import io.qalipsis.plugins.netty.http.client.monitoring.HttpStepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.http.request.HttpRequest
import io.qalipsis.plugins.netty.http.request.InternalHttpRequest
import io.qalipsis.plugins.netty.http.response.ResponseConverter
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.monitoring.StepBasedTcpMonitoringCollector
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.SocketClient
import io.qalipsis.plugins.netty.socket.SocketClientStep
import io.qalipsis.plugins.netty.tcp.spec.SocketClientPoolConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import io.qalipsis.plugins.netty.http.response.HttpResponse as QalipsisHttpResponse

/**
 * Step using several pools of [HttpClient]s, keeping a distinct pool of connection by remote peer.
 *
 * @author Eric Jessé
 */
internal class PooledHttpClientStep<I, O>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val ioCoroutineContext: CoroutineContext,
    private val ioCoroutineScope: CoroutineScope,
    private val requestFactory: suspend (StepContext<*, *>, I) -> HttpRequest<*>,
    private val clientConfiguration: HttpClientConfiguration,
    private val poolConfiguration: SocketClientPoolConfiguration,
    private val eventLoopGroupSupplier: EventLoopGroupSupplier,
    private val responseConverter: ResponseConverter<O>,
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: MeterRegistry?
) : AbstractStep<I, RequestResult<I, QalipsisHttpResponse<O>, *>>(id, retryPolicy),
    SocketClientStep<I, HttpRequest<*>, HttpResponse, RequestResult<I, QalipsisHttpResponse<O>, *>>,
    HttpClientStep<I, RequestResult<I, QalipsisHttpResponse<O>, *>> {

    /**
     * Map of all pools of clients created consecutively to calls to the different remote peer.
     */
    private val clientsPools = ConcurrentHashMap<SocketClient.RemotePeerIdentifier, Pool<HttpClient>>()

    private lateinit var stepMonitoringCollector: StepBasedTcpMonitoringCollector

    private lateinit var workerGroup: EventLoopGroup

    override suspend fun start(context: StepStartStopContext) {
        workerGroup = eventLoopGroupSupplier.getGroup()
        stepMonitoringCollector = StepBasedTcpMonitoringCollector(eventsLogger, meterRegistry, context, "http")
        clientsPools[SocketClient.RemotePeerIdentifier(clientConfiguration.inetAddress, clientConfiguration.port)] =
            createPool(clientConfiguration, workerGroup).awaitReadiness()
    }

    private fun createPool(
        clientConfiguration: HttpClientConfiguration,
        workerGroup: EventLoopGroup
    ): Pool<HttpClient> {
        return FixedPool(
            poolConfiguration.size,
            ioCoroutineContext,
            poolConfiguration.checkHealthBeforeUse,
            true,
            healthCheck = { it.isOpen },
            factory = { this.createClient(clientConfiguration, workerGroup) }
        )
    }

    private suspend fun createClient(connection: HttpClientConfiguration, workerGroup: EventLoopGroup): HttpClient {
        val cli = HttpClient(Long.MAX_VALUE, ioCoroutineScope, ioCoroutineContext)
        withContext(ioCoroutineContext) {
            cli.open(connection, workerGroup, stepMonitoringCollector)
        }
        return cli
    }

    override suspend fun stop(context: StepStartStopContext) {
        clientsPools.values.forEach { kotlin.runCatching { it.close() } }
        clientsPools.clear()
        workerGroup.shutdownGracefully()
    }

    override suspend fun execute(context: StepContext<I, RequestResult<I, QalipsisHttpResponse<O>, *>>) {
        val monitoringCollector = HttpStepContextBasedSocketMonitoringCollector(context, eventsLogger, meterRegistry)
        val input = context.receive()
        val response = withContext(ioCoroutineContext) {
            execute(monitoringCollector, context, input, requestFactory(context, input))
        }
        context.send(monitoringCollector.toResult(input, responseConverter.convert(response), null))
    }

    override suspend fun <IN> execute(
        monitoringCollector: StepContextBasedSocketMonitoringCollector,
        context: StepContext<*, *>,
        input: IN,
        request: HttpRequest<*>
    ): HttpResponse {
        var response = doExecute(monitoringCollector, context, request)
        var redirections = 0
        while (clientConfiguration.followRedirections && (++redirections) <= clientConfiguration.maxRedirections
            && response.status() in REDIRECTION_STATUS
        ) {
            val location = response.headers()[HttpHeaderNames.LOCATION]
            (response as? ReferenceCounted)?.release()
            val forwardedRequest = if (response.status() == HttpResponseStatus.SEE_OTHER) {
                (request as InternalHttpRequest<*, *>).with(location, HttpMethod.GET)
            } else {
                (request as InternalHttpRequest<*, *>).with(location)
            }
            response = doExecute(monitoringCollector, context, forwardedRequest)
        }

        return response
    }

    private suspend fun doExecute(
        monitoringCollector: StepContextBasedSocketMonitoringCollector,
        context: StepContext<*, *>,
        request: HttpRequest<*>
    ): HttpResponse {
        val requestUri = (request as InternalHttpRequest<*, *>).computeUri(clientConfiguration)
        val requestPeerIdentifier = SocketClient.RemotePeerIdentifier.of(requestUri)
        return clientsPools.computeIfAbsent(requestPeerIdentifier) {
            createPool(clientConfiguration.copy().apply { url(requestUri) }, workerGroup)
        }
            .awaitReadiness()
            .withPoolItem { client -> client.execute(context, request, monitoringCollector) }
    }

    /**
     * This has no effect on pooled steps.
     */
    override fun keepOpen() = Unit

    /**
     * This has no effect on pooled steps.
     */
    override suspend fun close(minionId: MinionId) = Unit

    /**
     * This has no effect on pooled steps.
     */
    override fun addUsage(count: Int) = Unit

    companion object {

        @JvmStatic
        private val REDIRECTION_STATUS = setOf(
            HttpResponseStatus.MOVED_PERMANENTLY,
            HttpResponseStatus.TEMPORARY_REDIRECT,
            HttpResponseStatus.PERMANENT_REDIRECT,
            HttpResponseStatus.SEE_OTHER
        )

        @JvmStatic
        private val log = logger()
    }
}
