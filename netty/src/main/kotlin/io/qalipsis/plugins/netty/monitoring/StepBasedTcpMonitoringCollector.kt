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

package io.qalipsis.plugins.netty.monitoring

import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
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
    private val meterRegistry: CampaignMeterRegistry?,
    context: StepStartStopContext,
    stepQualifier: String
) : SocketMonitoringCollector {

    override var cause: Throwable? = null

    private val eventPrefix = "netty.${stepQualifier}"

    private val meterPrefix = "netty-${stepQualifier}"

    private val tags = context.toEventTags()

    private val scenarioName = context.scenarioName

    private val stepName = context.stepName

    private val connectingCounter =
        meterRegistry?.counter(scenarioName, stepName, "${meterPrefix}-connecting", tags)

    private val connectedTimer = meterRegistry?.timer(scenarioName, stepName, "${meterPrefix}-connected", tags)

    private val connectionFailureTimer =
        meterRegistry?.timer(scenarioName, stepName, "${meterPrefix}-connection-failure", tags)

    private val tlsConnectedTimer =
        meterRegistry?.timer(scenarioName, stepName, "${meterPrefix}-tls-connected", tags)

    private val tlsConnectionFailureTimer =
        meterRegistry?.timer(scenarioName, stepName, "${meterPrefix}-tls-failure", tags)

    override fun recordConnecting() {
        eventsLogger?.info("${eventPrefix}.connecting", tags = tags)
        connectingCounter?.report {
            display("conn. attempts: %,.0f", ReportMessageSeverity.INFO) { count() }
        }?.increment()
    }

    override fun recordConnected(timeToConnect: Duration) {
        eventsLogger?.info("${eventPrefix}.connected", timeToConnect, tags = tags)
        connectedTimer?.report {
            display(
                "\u2713 %,.0f",
                severity = ReportMessageSeverity.INFO,
                row = 0,
                column = 1,
                Timer::count
            )
            display(
                "mean: %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 0,
                column = 2
            ) { this.mean(TimeUnit.MILLISECONDS) }
            display(
                "max: %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 0,
                column = 3
            ) { this.max(TimeUnit.MILLISECONDS) }
        }?.record(timeToConnect)
    }

    override fun recordConnectionFailure(timeToFailure: Duration, throwable: Throwable) {
        cause = throwable
        eventsLogger?.warn(
            "${eventPrefix}.connection-failure",
            arrayOf(timeToFailure, throwable),
            tags = tags
        )
        connectionFailureTimer?.report {
            display(
                "\u2716 %,.0f",
                severity = ReportMessageSeverity.ERROR,
                row = 0,
                column = 4,
                Timer::count
            )
        }?.record(timeToFailure)
    }

    override fun recordTlsHandshakeSuccess(timeToConnect: Duration) {
        eventsLogger?.info("${eventPrefix}.tls-connected", timeToConnect, tags = tags)
        tlsConnectedTimer?.report {
            display(
                "TLS: \u2713 %,.0f successes",
                severity = ReportMessageSeverity.INFO,
                row = 1,
                column = 0,
                Timer::count
            )
            display(
                "mean: %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 1,
                column = 1
            ) { this.mean(TimeUnit.MILLISECONDS) }
            display(
                "max: %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 1,
                column = 2
            ) { this.max(TimeUnit.MILLISECONDS) }
        }?.record(timeToConnect)
    }

    override fun recordTlsHandshakeFailure(timeToFailure: Duration, throwable: Throwable) {
        cause = throwable
        eventsLogger?.warn(
            "${eventPrefix}.tls-connection-failure",
            arrayOf(timeToFailure, throwable),
            tags = tags
        )
        tlsConnectionFailureTimer?.report {
            display(
                "\u2716 %,.0f failures",
                severity = ReportMessageSeverity.ERROR,
                row = 1,
                column = 3,
                Timer::count
            )
        }?.record(timeToFailure)
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
