package io.qalipsis.plugins.netty.http.client.monitoring

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.netty.handler.codec.http.HttpResponseStatus
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector

/**
 * Implementation of [StepContextBasedSocketMonitoringCollector] for HTTP.
 */
internal class HttpStepContextBasedSocketMonitoringCollector(
    stepContext: StepContext<*, *>,
    eventsLogger: EventsLogger?,
    meterRegistry: MeterRegistry?,
) : StepContextBasedSocketMonitoringCollector(
    stepContext, eventsLogger, meterRegistry, "http"
) {

    /**
     * Records the status of the HTTP response.
     */
    fun recordHttpStatus(status: HttpResponseStatus) {
        eventsLogger?.info(
            "${eventPrefix}.http-status",
            status.code(),
            tags = eventTags
        )
        meterRegistry?.counter("${metersPrefix}-http-status", metersTags + Tag.of("status", status.code().toString()))
            ?.increment()
    }

}
