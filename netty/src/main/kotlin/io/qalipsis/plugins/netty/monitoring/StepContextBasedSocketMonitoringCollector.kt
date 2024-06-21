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

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.plugins.netty.socket.SocketMonitoringCollector
import io.qalipsis.plugins.netty.tcp.ConnectionAndRequestResult
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal open class StepContextBasedSocketMonitoringCollector(
    private val stepContext: StepContext<*, *>,
    protected val eventsLogger: EventsLogger?,
    protected val meterRegistry: CampaignMeterRegistry?,
    stepQualifier: String
) : SocketMonitoringCollector {

    var connected = false

    var connectionFailure: Throwable? = null

    var tlsFailure: Throwable? = null

    var sendingFailure: Throwable? = null

    private val firstSentByteInstant = AtomicLong()

    private var sendingInstants = concurrentList<Long>()

    override val cause: Throwable?
        get() = connectionFailure ?: tlsFailure ?: sendingFailure

    val meters = ConnectionAndRequestResult.MetersImpl()

    protected val eventPrefix = "netty.${stepQualifier}"

    protected val meterPrefix = "netty-${stepQualifier}"

    protected val eventTags = stepContext.toEventTags().toMutableMap()

    protected val metersTags = stepContext.toMetersTags().toMutableMap()

    private val scenarioName = stepContext.scenarioName

    private val stepName = stepContext.stepName

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

    private val sendingRequestCounter =
        meterRegistry?.counter(scenarioName, stepName, "${meterPrefix}-sending-request", metersTags)?.report {
            display("\u2197 Reqs.", ReportMessageSeverity.INFO, row = 1) { 0 }
            display("\u27B6 %,.0f", ReportMessageSeverity.INFO, row = 1, column = 1, Counter::count)
        }

    private val sentRequestCounter =
        meterRegistry?.counter(scenarioName, stepName, "${meterPrefix}-sent-request", metersTags)?.report {
            display("\u2713 %,.0f reqs", ReportMessageSeverity.INFO, row = 1, column = 2, Counter::count)
        }

    private val sendingBytesCounter =
        meterRegistry?.counter(scenarioName, stepName, "${meterPrefix}-sending-bytes", metersTags)?.report {
            display("\u27B6 %,.0f bytes", ReportMessageSeverity.INFO, row = 1, column = 3, Counter::count)
        }

    private val sentBytesCounter =
        meterRegistry?.counter(scenarioName, stepName, "${meterPrefix}-sent-bytes", metersTags)?.report {
            display("\u2713 %,.0f bytes", ReportMessageSeverity.INFO, row = 1, column = 4, Counter::count)
        }

    private val sendingRequestFailureCounter =
        meterRegistry?.counter(scenarioName, stepName, "${meterPrefix}-sending-request-failure", metersTags)?.report {
            display("\u2716 %,.0f reqs", ReportMessageSeverity.ERROR, row = 1, column = 5, Counter::count)
        }

    private val sendingBytesFailureCounter =
        meterRegistry?.counter(scenarioName, stepName, "${meterPrefix}-sending-failure", metersTags)

    private val receivingDataTimer =
        meterRegistry?.timer(scenarioName, stepName, "${meterPrefix}-receiving", metersTags)?.report {
            display("\u2198 Resp.", ReportMessageSeverity.INFO, row = 2) { 0 }
            display("1st byte", ReportMessageSeverity.INFO, row = 2, column = 1) { 0 }
            display(
                "mean: %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 2,
                column = 2
            ) { this.mean(TimeUnit.MILLISECONDS) }
            display(
                "max: %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 2,
                column = 3
            ) { this.max(TimeUnit.MILLISECONDS) }
        }

    private val receivedDataTimer by lazy {
        meterRegistry?.timer(scenarioName, stepName, "${meterPrefix}-received-response", metersTags)?.report {
            display("\nlast byte", ReportMessageSeverity.INFO, row = 2, column = 1) { 0 }
            display(
                "\n      %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 2,
                column = 2
            ) { this.mean(TimeUnit.MILLISECONDS) }
            display(
                "\n     %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 2,
                column = 3
            ) { this.max(TimeUnit.MILLISECONDS) }
        }
    }

    private val receivingDataFailureCounter =
        meterRegistry?.counter(scenarioName, stepName, "${meterPrefix}-receiving-failure", metersTags)

    fun setTags(vararg tags: Pair<String, String>) {
        this.eventTags.clear()
        this.eventTags.putAll(stepContext.toEventTags())
        this.eventTags.putAll(tags)

        this.metersTags.clear()
        this.metersTags.putAll(stepContext.toMetersTags())
        this.metersTags.putAll(tags)
    }

    override fun recordConnecting() {
        eventsLogger?.info("${eventPrefix}.connecting", tags = eventTags)
        connectingCounter?.increment()
    }

    override fun recordConnected(timeToConnect: Duration) {
        connected = true
        meters.timeToSuccessfulConnect = timeToConnect
        eventsLogger?.info("${eventPrefix}.connected", timeToConnect, tags = eventTags)
        connectedTimer?.record(timeToConnect)
    }

    override fun recordConnectionFailure(timeToFailure: Duration, throwable: Throwable) {
        if (connectionFailure == null) {
            meters.timeToFailedConnect = timeToFailure
            connected = false
            connectionFailure = throwable
            eventsLogger?.warn(
                "${eventPrefix}.connection-failure",
                arrayOf(timeToFailure, throwable),
                tags = eventTags
            )
            connectionFailureTimer?.record(timeToFailure)
        }
    }

    override fun recordTlsHandshakeSuccess(timeToConnect: Duration) {
        connected = true
        meters.timeToSuccessfulTlsConnect = timeToConnect
        eventsLogger?.info("${eventPrefix}.tls-connected", timeToConnect, tags = eventTags)
        tlsConnectedTimer?.record(timeToConnect)
    }

    override fun recordTlsHandshakeFailure(timeToFailure: Duration, throwable: Throwable) {
        if (tlsFailure == null) {
            eventsLogger?.warn(
                "${eventPrefix}.tls-connection-failure",
                arrayOf(timeToFailure, throwable),
                tags = eventTags
            )
            tlsConnectionFailureTimer?.record(timeToFailure)

            connected = false
            meters.timeToFailedTlsConnect = timeToFailure
            tlsFailure = throwable
        }
    }

    override fun recordSendingRequest() {
        eventsLogger?.info("${eventPrefix}.sending-request", tags = eventTags)
        sendingRequestCounter?.increment()
    }

    override fun recordSendingData(bytesCount: Int) {
        val now = System.nanoTime()
        sendingInstants.add(now)
        firstSentByteInstant.compareAndSet(0, now)
        if (bytesCount > 0) {
            eventsLogger?.debug("${eventPrefix}.sending-bytes", bytesCount, tags = eventTags)
        } else {
            eventsLogger?.trace("${eventPrefix}.sending-bytes", bytesCount, tags = eventTags)
        }
        sendingBytesCounter?.increment(bytesCount.toDouble())
        meters.bytesCountToSend += bytesCount
    }

    override fun recordSentRequestSuccess() {
        eventsLogger?.info("${eventPrefix}.sent-request", tags = eventTags)
        sentRequestCounter?.increment()
    }

    override fun recordSentDataSuccess(bytesCount: Int) {
        val timeToSent = Duration.ofNanos(System.nanoTime() - sendingInstants.removeAt(0))
        if (bytesCount > 0) {
            eventsLogger?.debug("${eventPrefix}.sent-bytes", arrayOf(timeToSent, bytesCount), tags = eventTags)
        } else {
            eventsLogger?.trace("${eventPrefix}.sent-bytes", arrayOf(timeToSent, bytesCount), tags = eventTags)
        }
        sentBytesCounter?.increment(bytesCount.toDouble())
        meters.sentBytes += bytesCount
    }

    override fun recordSentDataFailure(throwable: Throwable) {
        val timeToFailure = Duration.ofNanos(System.nanoTime() - firstSentByteInstant.get())
        if (sendingFailure == null) {
            sendingFailure = throwable
            eventsLogger?.warn("${eventPrefix}.sending.failed", arrayOf(timeToFailure, throwable), tags = eventTags)
            sendingBytesFailureCounter?.increment()
        }
    }

    override fun recordSentRequestFailure(throwable: Throwable) {
        eventsLogger?.warn("${eventPrefix}.sending-request-failure", tags = eventTags)
        sendingRequestFailureCounter?.increment()
    }

    override fun recordReceivingData() {
        meters.timeToFirstByte = Duration.ofNanos(System.nanoTime() - firstSentByteInstant.get())
        eventsLogger?.debug("${eventPrefix}.receiving", meters.timeToFirstByte, tags = eventTags)
        receivingDataTimer?.record(meters.timeToFirstByte!!)
    }

    override fun countReceivedData(bytesCount: Int) {
        meters.receivedBytes += bytesCount
    }

    override fun recordReceptionComplete() {
        meters.timeToLastByte = Duration.ofNanos(System.nanoTime() - firstSentByteInstant.get())
        eventsLogger?.info(
            "${eventPrefix}.received-response",
            arrayOf(meters.timeToLastByte, meters.receivedBytes),
            tags = eventTags
        )
        receivedDataTimer?.record(meters.timeToLastByte!!)
    }

    override fun recordReceivingDataFailure(throwable: Throwable) {
        val timeToFailure = Duration.ofNanos(System.nanoTime() - firstSentByteInstant.get())
        if (sendingFailure == null) {
            sendingFailure = throwable
            eventsLogger?.warn(
                "${eventPrefix}.receiving.failed",
                arrayOf(timeToFailure, throwable),
                tags = eventTags
            )
            receivingDataFailureCounter?.increment()
        }
    }

    fun <IN, OUT : Any> toResult(input: IN, response: OUT?, failure: Throwable?) = ConnectionAndRequestResult(
        connected,
        connectionFailure,
        tlsFailure,
        sendingFailure,
        failure,
        input,
        response,
        meters
    )

}
