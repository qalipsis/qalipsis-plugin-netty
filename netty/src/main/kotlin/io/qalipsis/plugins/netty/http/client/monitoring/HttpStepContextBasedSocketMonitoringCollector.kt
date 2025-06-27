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

package io.qalipsis.plugins.netty.http.client.monitoring

import io.netty.handler.codec.http.HttpResponseStatus
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.report.ReportMessageSeverity
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

    private val scenarioName = stepContext.scenarioName

    private val stepName = stepContext.stepName

    /**
     * Records the status of the HTTP response.
     */
    fun recordHttpStatus(status: HttpResponseStatus) {
        eventsLogger?.info(
            "${eventPrefix}.status",
            status.code(),
            tags = eventTags
        )
        meterRegistry?.counter(
            scenarioName,
            stepName,
            "${meterPrefix}-status",
            metersTags + ("status" to status.code().toString())
        )?.report {
            display("HTTP Status", ReportMessageSeverity.INFO, row = 3) { 0 }
            display(
                "${status.code()}: %,.0f\n",
                ReportMessageSeverity.INFO,
                row = 3,
                column = 1,
                Counter::count
            )
        }?.increment()
    }

}
