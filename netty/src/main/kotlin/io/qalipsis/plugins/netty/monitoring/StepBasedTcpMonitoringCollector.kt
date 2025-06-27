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

package io.qalipsis.plugins.netty.monitoring

import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.plugins.netty.socket.SocketMonitoringCollector
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Implementation of [MonitoringCollector] to log information relatively to a [io.qalipsis.api.steps.Step].
 *
 * @author Eric Jessé
 */
internal class StepBasedTcpMonitoringCollector(
    private val eventsLogger: EventsLogger?,
    meterRegistry: CampaignMeterRegistry?,
    context: StepStartStopContext,
    stepQualifier: String
) : SocketMonitoringCollector {

    override var cause: Throwable? = null

    private val eventPrefix = "netty.${stepQualifier}"

    private val meterPrefix = "netty-${stepQualifier}"

    private val eventsTags = context.toEventTags()

    private val metersTags = context.toMetersTags()

    private val scenarioName = context.scenarioName

    private val stepName = context.stepName


    private val connectingCounter by lazy {
        meterRegistry?.counter(scenarioName, stepName, "${meterPrefix}-connecting", metersTags)?.report {
            display("conn.", ReportMessageSeverity.INFO) { 0 }
            display("\u27B6 %,.0f", ReportMessageSeverity.INFO, column = 1, toNumber = Counter::count)
        }
    }

    private val connectedTimer by lazy {
        meterRegistry?.timer(scenarioName, stepName, "${meterPrefix}-connected", metersTags)?.report {
            display(
                "\u2713 %,.0f",
                severity = ReportMessageSeverity.INFO,
                row = 0,
                column = 2,
                Timer::count
            )
            display(
                "mean: %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 0,
                column = 3
            ) { this.mean(TimeUnit.MILLISECONDS) }
            display(
                "max: %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 0,
                column = 4
            ) { this.max(TimeUnit.MILLISECONDS) }
        }
    }

    private val connectionFailureTimer by lazy {
        meterRegistry?.timer(scenarioName, stepName, "${meterPrefix}-connection-failure", metersTags)?.report {
            display(
                "\u2716 %,.0f",
                severity = ReportMessageSeverity.ERROR,
                row = 0,
                column = 5,
                Timer::count
            )
        }
    }

    private val tlsConnectedTimer by lazy {
        meterRegistry?.timer(scenarioName, stepName, "${meterPrefix}-tls-connected", metersTags)?.report {
            display("\nTLS", ReportMessageSeverity.INFO, row = 0) { 0 }
            display(
                "\n\u2713 %,.0f",
                severity = ReportMessageSeverity.INFO,
                row = 0,
                column = 2,
                Timer::count
            )
            display(
                "\n       %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 0,
                column = 3
            ) { this.mean(TimeUnit.MILLISECONDS) }
            display(
                "\n     %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 0,
                column = 4
            ) { this.max(TimeUnit.MILLISECONDS) }
        }
    }

    private val tlsConnectionFailureTimer by lazy {
        meterRegistry?.timer(scenarioName, stepName, "${meterPrefix}-tls-failure", metersTags)?.report {
            display(
                "\u2716 %,.0f",
                severity = ReportMessageSeverity.ERROR,
                row = 0,
                column = 5,
                Timer::count
            )
        }
    }

    override fun recordConnecting() {
        eventsLogger?.info("${eventPrefix}.connecting", tags = eventsTags)
        connectingCounter?.increment()
    }

    override fun recordConnected(timeToConnect: Duration) {
        eventsLogger?.info("${eventPrefix}.connected", timeToConnect, tags = eventsTags)
        connectedTimer?.record(timeToConnect)
    }

    override fun recordConnectionFailure(timeToFailure: Duration, throwable: Throwable) {
        cause = throwable
        eventsLogger?.warn(
            "${eventPrefix}.connection-failure",
            arrayOf(timeToFailure, throwable),
            tags = eventsTags
        )
        connectionFailureTimer?.record(timeToFailure)
    }

    override fun recordTlsHandshakeSuccess(timeToConnect: Duration) {
        eventsLogger?.info("${eventPrefix}.tls-connected", timeToConnect, tags = eventsTags)
        tlsConnectedTimer?.record(timeToConnect)
    }

    override fun recordTlsHandshakeFailure(timeToFailure: Duration, throwable: Throwable) {
        cause = throwable
        eventsLogger?.warn(
            "${eventPrefix}.tls-connection-failure",
            arrayOf(timeToFailure, throwable),
            tags = eventsTags
        )
        tlsConnectionFailureTimer?.record(timeToFailure)
    }

    override fun recordSendingData(bytesCount: Int) {
        // No-op
    }

    override fun recordSentDataSuccess(bytesCount: Int) {
        // No-op
    }

    override fun recordSentDataFailure(throwable: Throwable) {
        // No-op
    }

    override fun recordReceivingData() {
        // No-op
    }

    override fun countReceivedData(bytesCount: Int) {
        // No-op
    }

    override fun recordReceptionComplete() {
        // No-op
    }

    override fun recordReceivingDataFailure(throwable: Throwable) {
        // No-op
    }

    override fun recordSendingRequest() {
        // No-op
    }

    override fun recordSentRequestSuccess() {
        // No-op
    }

    override fun recordSentRequestFailure(throwable: Throwable) {
        // No-op
    }
}
