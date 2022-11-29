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

package io.qalipsis.plugins.netty.http.client.monitoring

import io.micrometer.core.instrument.Tag
import io.netty.handler.codec.http.HttpResponseStatus
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector

/**
 * Implementation of [StepContextBasedSocketMonitoringCollector] for HTTP.
 */
internal class HttpStepContextBasedSocketMonitoringCollector(
    stepContext: StepContext<*, *>,
    eventsLogger: EventsLogger?,
    meterRegistry: CampaignMeterRegistry?,
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
