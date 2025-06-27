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

package io.qalipsis.plugins.netty.udp

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.plugins.netty.monitoring.MonitoringCollector
import java.time.Duration

internal class UdpMonitoringCollector(
    private val stepContext: StepContext<*, *>,
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: CampaignMeterRegistry?,
    stepQualifier: String
) : MonitoringCollector {

    var sendingFailure: Throwable? = null

    val cause: Throwable?
        get() = sendingFailure

    val metrics = UdpResult.MetersImpl()

    private val eventPrefix = "netty.${stepQualifier}"

    private val meterPrefix = "netty-${stepQualifier}"

    private var sendingStart: Long = 0

    private var receivedBytesCount: Int = 0

    private val tags = stepContext.toEventTags()

    private val scenarioName = stepContext.scenarioName

    private val stepName = stepContext.stepName

    override fun recordSendingRequest() {
        eventsLogger?.info("${eventPrefix}.sending-request", tags = tags)
        meterRegistry?.counter(scenarioName, stepName, "${meterPrefix}-sending-request", tags)
    }

    override fun recordSendingData(bytesCount: Int) {
        sendingStart = System.nanoTime()
        if (bytesCount > 0) {
            eventsLogger?.debug("${eventPrefix}.sending-bytes", bytesCount, tags = tags)
        } else {
            eventsLogger?.trace("${eventPrefix}.sending-bytes", bytesCount, tags = tags)
        }
        meterRegistry?.counter(scenarioName, stepName, "${meterPrefix}-sending-bytes", tags)
            ?.increment(bytesCount.toDouble())
        metrics.bytesCountToSend = bytesCount
    }

    override fun recordSentDataSuccess(bytesCount: Int) {
        val timeToSent = Duration.ofNanos(System.nanoTime() - sendingStart)
        if (bytesCount > 0) {
            eventsLogger?.debug("${eventPrefix}.sent-bytes", arrayOf(timeToSent, bytesCount), tags = tags)
        } else {
            eventsLogger?.trace("${eventPrefix}.sent-bytes", arrayOf(timeToSent, bytesCount), tags = tags)
        }
        meterRegistry?.counter(scenarioName, stepName, "${meterPrefix}-sent-bytes", tags)
            ?.increment(bytesCount.toDouble())
        metrics.sentBytes = bytesCount
    }

    override fun recordSentDataFailure(throwable: Throwable) {
        val timeToFailure = Duration.ofNanos(System.nanoTime() - sendingStart)
        sendingFailure = throwable
        eventsLogger?.warn(
            "${eventPrefix}.sending.failed",
            arrayOf(timeToFailure, throwable),
            tags = tags
        )
        meterRegistry?.counter(scenarioName, stepName, "${meterPrefix}-sending-failure", tags)?.increment()

        stepContext.addError(StepError(throwable))
    }

    override fun recordSentRequestSuccess() {
        eventsLogger?.info("${eventPrefix}.sent-request", tags = tags)
        meterRegistry?.counter(scenarioName, stepName, "${meterPrefix}-sent-request", tags)
    }

    override fun recordSentRequestFailure(throwable: Throwable) {
        eventsLogger?.warn("${eventPrefix}.sending-request-failure", tags = tags)
        meterRegistry?.counter(scenarioName, stepName, "${meterPrefix}-sending-request-failure", tags)
    }

    override fun recordReceivingData() {
        val timeToResponse = Duration.ofNanos(System.nanoTime() - sendingStart)
        metrics.timeToFirstByte = timeToResponse
        eventsLogger?.debug("${eventPrefix}.receiving", timeToResponse, tags = tags)
        meterRegistry?.counter(scenarioName, stepName, "${meterPrefix}-receiving", tags)?.increment()
    }

    override fun countReceivedData(bytesCount: Int) {
        receivedBytesCount += bytesCount
    }

    override fun recordReceptionComplete() {
        val timeToCompleteResponse = Duration.ofNanos(System.nanoTime() - sendingStart)
        metrics.receivedBytes = receivedBytesCount
        metrics.timeToLastByte = timeToCompleteResponse
        eventsLogger?.info(
            "${eventPrefix}.received-bytes",
            arrayOf(timeToCompleteResponse, receivedBytesCount),
            tags = tags
        )
        meterRegistry?.counter(scenarioName, stepName, "${meterPrefix}-received", tags)
            ?.increment(receivedBytesCount.toDouble())
    }

    override fun recordReceivingDataFailure(throwable: Throwable) {
        val timeToFailure = Duration.ofNanos(System.nanoTime() - sendingStart)
        sendingFailure = throwable
        eventsLogger?.warn(
            "${eventPrefix}.receiving.failed",
            arrayOf(timeToFailure, throwable),
            tags = tags
        )
        meterRegistry?.counter(scenarioName, stepName, "${meterPrefix}-receiving-failure", tags)?.increment()
    }

    fun <IN> toResult(input: IN, response: ByteArray?, failure: Throwable?) = UdpResult(
        sendingFailure,
        failure,
        input,
        response,
        metrics
    )

}
