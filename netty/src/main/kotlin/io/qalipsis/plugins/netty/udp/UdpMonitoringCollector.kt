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

    private val eventTags = stepContext.toEventTags()

    private val metersTags = stepContext.toMetersTags()

    override fun recordSendingRequest() {
        eventsLogger?.info("${eventPrefix}.sending-request", tags = eventTags)
        meterRegistry?.counter("${meterPrefix}-sending-request", metersTags)
    }

    override fun recordSendingData(bytesCount: Int) {
        sendingStart = System.nanoTime()
        if (bytesCount > 0) {
            eventsLogger?.debug("${eventPrefix}.sending-bytes", bytesCount, tags = eventTags)
        } else {
            eventsLogger?.trace("${eventPrefix}.sending-bytes", bytesCount, tags = eventTags)
        }
        meterRegistry?.counter("${meterPrefix}-sending-bytes", metersTags)
            ?.increment(bytesCount.toDouble())
        metrics.bytesCountToSend = bytesCount
    }

    override fun recordSentDataSuccess(bytesCount: Int) {
        val timeToSent = Duration.ofNanos(System.nanoTime() - sendingStart)
        if (bytesCount > 0) {
            eventsLogger?.debug("${eventPrefix}.sent-bytes", arrayOf(timeToSent, bytesCount), tags = eventTags)
        } else {
            eventsLogger?.trace("${eventPrefix}.sent-bytes", arrayOf(timeToSent, bytesCount), tags = eventTags)
        }
        meterRegistry?.counter("${meterPrefix}-sent-bytes", metersTags)
            ?.increment(bytesCount.toDouble())
        metrics.sentBytes = bytesCount
    }

    override fun recordSentDataFailure(throwable: Throwable) {
        val timeToFailure = Duration.ofNanos(System.nanoTime() - sendingStart)
        sendingFailure = throwable
        eventsLogger?.warn(
            "${eventPrefix}.sending.failed",
            arrayOf(timeToFailure, throwable),
            tags = eventTags
        )
        meterRegistry?.counter("${meterPrefix}-sending-failure", metersTags)?.increment()

        stepContext.addError(StepError(throwable))
    }

    override fun recordSentRequestSuccess() {
        eventsLogger?.info("${eventPrefix}.sent-request", tags = eventTags)
        meterRegistry?.counter("${meterPrefix}-sent-request", metersTags)
    }

    override fun recordSentRequestFailure(throwable: Throwable) {
        eventsLogger?.warn("${eventPrefix}.sending-request-failure", tags = eventTags)
        meterRegistry?.counter("${meterPrefix}-sending-request-failur", metersTags)
    }

    override fun recordReceivingData() {
        val timeToResponse = Duration.ofNanos(System.nanoTime() - sendingStart)
        metrics.timeToFirstByte = timeToResponse
        eventsLogger?.debug("${eventPrefix}.receiving", timeToResponse, tags = eventTags)
        meterRegistry?.counter("${meterPrefix}-receiving", metersTags)?.increment()
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
            tags = eventTags
        )
        meterRegistry?.counter("${meterPrefix}-received", metersTags)
            ?.increment(receivedBytesCount.toDouble())
    }

    override fun recordReceivingDataFailure(throwable: Throwable) {
        val timeToFailure = Duration.ofNanos(System.nanoTime() - sendingStart)
        sendingFailure = throwable
        eventsLogger?.warn(
            "${eventPrefix}.receiving.failed",
            arrayOf(timeToFailure, throwable),
            tags = eventTags
        )
        meterRegistry?.counter("${meterPrefix}-receiving-failure", metersTags)?.increment()
    }

    fun <IN> toResult(input: IN, response: ByteArray?, failure: Throwable?) = UdpResult(
        sendingFailure,
        failure,
        input,
        response,
        metrics
    )

}
