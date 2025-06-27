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

package io.qalipsis.plugins.netty.http

import io.netty.handler.codec.http.HttpResponse
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.http.client.monitoring.HttpStepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.http.response.ResponseConverter
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.QuerySocketClientStep
import io.qalipsis.plugins.netty.http.request.HttpRequest as QalipsisHttpRequest
import io.qalipsis.plugins.netty.http.response.HttpResponse as QalipsisHttpResponse

/**
 * Step to perform HTTP operations onto a server, reusing the same connection from a past action.
 *
 * @author Eric Jessé
 */
internal class QueryHttpClientStep<I, O>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    connectionOwner: HttpClientStep<*, *>,
    requestFactory: suspend HttpRequestBuilder.(StepContext<*, *>, I) -> QalipsisHttpRequest<*>,
    private val responseConverter: ResponseConverter<O>,
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: CampaignMeterRegistry?
) : QuerySocketClientStep<I, QalipsisHttpResponse<O>, QalipsisHttpRequest<*>, HttpResponse, HttpRequestBuilder, HttpClientStep<*, *>>(
    id,
    retryPolicy,
    connectionOwner,
    "with-http",
    HttpRequestBuilderImpl,
    requestFactory,
    eventsLogger,
    meterRegistry
) {

    override fun createMonitoringCollector(context: StepContext<I, RequestResult<I, QalipsisHttpResponse<O>, *>>): StepContextBasedSocketMonitoringCollector {
        return HttpStepContextBasedSocketMonitoringCollector(context, eventsLogger, meterRegistry)
    }

    override fun convertResponseToOutput(response: HttpResponse): QalipsisHttpResponse<O> {
        return responseConverter.convert(response)
    }
}
