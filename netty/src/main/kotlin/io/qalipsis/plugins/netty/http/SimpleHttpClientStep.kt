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

import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.util.ReferenceCounted
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.http.client.MultiSocketHttpClient
import io.qalipsis.plugins.netty.http.client.monitoring.HttpStepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.http.request.HttpRequest
import io.qalipsis.plugins.netty.http.request.InternalHttpRequest
import io.qalipsis.plugins.netty.http.response.ResponseConverter
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.SimpleSocketClientStep
import io.qalipsis.plugins.netty.tcp.ConnectionAndRequestResult
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import io.qalipsis.plugins.netty.http.response.HttpResponse as QalipsisHttpResponse

/**
 * Step to send and receive data using HTTP, using a per-minion connection strategy.
 *
 * @author Eric Jessé
 */
internal class SimpleHttpClientStep<I, O>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val ioCoroutineScope: CoroutineScope,
    private val ioCoroutineContext: CoroutineContext,
    requestFactory: suspend HttpRequestBuilder.(StepContext<*, *>, I) -> HttpRequest<*>,
    private val clientConfiguration: HttpClientConfiguration,
    eventLoopGroupSupplier: EventLoopGroupSupplier,
    private val responseConverter: ResponseConverter<O>,
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: CampaignMeterRegistry?
) : SimpleSocketClientStep<I, QalipsisHttpResponse<O>, HttpClientConfiguration, HttpRequest<*>, HttpResponse, HttpRequestBuilder, MultiSocketHttpClient>(
    id,
    retryPolicy,
    HttpRequestBuilderImpl,
    requestFactory,
    clientConfiguration,
    "http",
    eventLoopGroupSupplier,
    eventsLogger,
    meterRegistry
), HttpClientStep<I, ConnectionAndRequestResult<I, QalipsisHttpResponse<O>>> {

    public override suspend fun createClient(
        minionId: MinionId,
        monitoringCollector: StepContextBasedSocketMonitoringCollector
    ): MultiSocketHttpClient {
        log.trace { "Creating the HTTP client for minion $minionId" }
        val numberOfPlannedUsages =
            if (clientConfiguration.keepConnectionAlive) Long.MAX_VALUE else usagesCount.get().toLong()
        val cli = MultiSocketHttpClient(numberOfPlannedUsages, ioCoroutineScope, ioCoroutineContext) {
            // When closing, remove the client and the usage counter from the cache.
            clients.remove(minionId)?.close()
            clientsInUse.remove(minionId)
        }
        cli.open(clientConfiguration, workerGroup, monitoringCollector)
        return cli
    }

    override fun createMonitoringCollector(context: StepContext<I, ConnectionAndRequestResult<I, QalipsisHttpResponse<O>>>): StepContextBasedSocketMonitoringCollector {
        return HttpStepContextBasedSocketMonitoringCollector(context, eventsLogger, meterRegistry)
    }

    override fun convertResponseToOutput(response: HttpResponse): QalipsisHttpResponse<O> {
        return responseConverter.convert(response)
    }

    override suspend fun <IN> execute(
        monitoringCollector: StepContextBasedSocketMonitoringCollector,
        context: StepContext<*, *>,
        input: IN,
        request: HttpRequest<*>
    ): HttpResponse {
        var redirections = 0
        var response = doExecute(monitoringCollector, context, input, request)
        while (clientConfiguration.followRedirections && (++redirections) <= clientConfiguration.maxRedirections
            && response.status() in REDIRECTION_STATUS
        ) {
            val location = response.headers()[HttpHeaderNames.LOCATION]
            response.apply {
                if (this is ReferenceCounted && this.refCnt() > 0) this.release(this.refCnt())
            }
            val forwardedRequest = if (response.status() == HttpResponseStatus.SEE_OTHER) {
                (request as InternalHttpRequest<*, *>).with(location, HttpMethod.GET)
            } else {
                (request as InternalHttpRequest<*, *>).with(location)
            }
            response = doExecute(monitoringCollector, context, input, forwardedRequest)
        }

        return response
    }


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

