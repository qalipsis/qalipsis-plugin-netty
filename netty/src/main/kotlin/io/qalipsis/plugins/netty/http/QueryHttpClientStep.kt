package io.qalipsis.plugins.netty.http

import io.micrometer.core.instrument.MeterRegistry
import io.netty.handler.codec.http.HttpResponse
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.http.client.monitoring.HttpStepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.http.response.ResponseConverter
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.QuerySocketClientStep
import kotlin.coroutines.CoroutineContext
import io.qalipsis.plugins.netty.http.request.HttpRequest as QalipsisHttpRequest
import io.qalipsis.plugins.netty.http.response.HttpResponse as QalipsisHttpResponse

/**
 * Step to perform HTTP operations onto a server, reusing the same connection from a past action.
 *
 * @author Eric Jessé
 */
internal class QueryHttpClientStep<I, O>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    ioCoroutineContext: CoroutineContext,
    connectionOwner: HttpClientStep<*, *>,
    requestFactory: suspend (StepContext<*, *>, I) -> QalipsisHttpRequest<*>,
    private val responseConverter: ResponseConverter<O>,
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: MeterRegistry?
) : QuerySocketClientStep<I, QalipsisHttpResponse<O>, QalipsisHttpRequest<*>, HttpResponse, HttpClientStep<*, *>>(
    id,
    retryPolicy,
    ioCoroutineContext,
    connectionOwner,
    "with-http",
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
