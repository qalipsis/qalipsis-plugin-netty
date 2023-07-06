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

import io.micrometer.core.instrument.Tag
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

    protected var metersTags = stepContext.toMetersTags()

    fun setTags(vararg tags: Pair<String, String>) {
        eventTags.clear()
        eventTags.putAll(stepContext.toEventTags())
        eventTags.putAll(tags)

        metersTags = stepContext.toMetersTags().and(tags.map { (key, value) -> Tag.of(key, value) })
    }

    override fun recordConnecting() {
        eventsLogger?.info("${eventPrefix}.connecting", tags = eventTags)
        meterRegistry?.counter("${meterPrefix}-connecting", metersTags)?.report {
            display("conn. attempts: %,.0f", ReportMessageSeverity.INFO) { count() }
        }?.increment()
    }

    override fun recordConnected(timeToConnect: Duration) {
        connected = true
        meters.timeToSuccessfulConnect = timeToConnect
        eventsLogger?.info("${eventPrefix}.connected", timeToConnect, tags = eventTags)
        meterRegistry?.timer("${meterPrefix}-connected", metersTags)?.report {
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
        if (connectionFailure == null) {
            meters.timeToFailedConnect = timeToFailure
            connected = false
            connectionFailure = throwable
            eventsLogger?.warn(
                "${eventPrefix}.connection-failure",
                arrayOf(timeToFailure, throwable),
                tags = eventTags
            )
            meterRegistry?.timer("${meterPrefix}-connection-failure", metersTags)?.report {
                display(
                    "\u2716 %,.0f",
                    severity = ReportMessageSeverity.ERROR,
                    row = 0,
                    column = 4,
                    Timer::count
                )
            }?.record(timeToFailure)
        }
    }

    override fun recordTlsHandshakeSuccess(timeToConnect: Duration) {
        connected = true
        meters.timeToSuccessfulTlsConnect = timeToConnect
        eventsLogger?.info("${eventPrefix}.tls-connected", timeToConnect, tags = eventTags)
        meterRegistry?.timer("${meterPrefix}-tls-connected", metersTags)?.report {
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
        if (tlsFailure == null) {
            eventsLogger?.warn(
                "${eventPrefix}.tls-connection-failure",
                arrayOf(timeToFailure, throwable),
                tags = eventTags
            )
            meterRegistry?.timer("${meterPrefix}-tls-failure", metersTags)?.report {
                display(
                    "\u2716 %,.0f failures",
                    severity = ReportMessageSeverity.ERROR,
                    row = 1,
                    column = 3,
                    Timer::count
                )
            }?.record(timeToFailure)

            connected = false
            meters.timeToFailedTlsConnect = timeToFailure
            tlsFailure = throwable
        }
    }

    override fun recordSendingRequest() {
        eventsLogger?.info("${eventPrefix}.sending-request", tags = eventTags)
        meterRegistry?.counter("${meterPrefix}-sending-request", metersTags)?.report {
            display("\u2197 %,.0f req", ReportMessageSeverity.INFO, row = 2, column = 0, Counter::count)
        }?.increment()
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
        meterRegistry?.counter("${meterPrefix}-sending-bytes", metersTags)?.report {
            display("%,.0f bytes", ReportMessageSeverity.INFO, row = 2, column = 1, Counter::count)
        }?.increment(bytesCount.toDouble())
        meters.bytesCountToSend += bytesCount
    }

    override fun recordSentRequestSuccess() {
        eventsLogger?.info("${eventPrefix}.sent-request", tags = eventTags)
        meterRegistry?.counter("${meterPrefix}-sent-request", metersTags)?.report {
            display("\u2713 %,.0f req", ReportMessageSeverity.INFO, row = 2, column = 2, Counter::count)
        }?.increment()
    }

    override fun recordSentDataSuccess(bytesCount: Int) {
        val timeToSent = Duration.ofNanos(System.nanoTime() - sendingInstants.removeAt(0))
        if (bytesCount > 0) {
            eventsLogger?.debug("${eventPrefix}.sent-bytes", arrayOf(timeToSent, bytesCount), tags = eventTags)
        } else {
            eventsLogger?.trace("${eventPrefix}.sent-bytes", arrayOf(timeToSent, bytesCount), tags = eventTags)
        }
        meterRegistry?.counter("${meterPrefix}-sent-bytes", metersTags)?.report {
            display("\u2713 %,.0f bytes", ReportMessageSeverity.INFO, row = 2, column = 3, Counter::count)
        }?.increment(bytesCount.toDouble())
        meters.sentBytes += bytesCount
    }

    override fun recordSentDataFailure(throwable: Throwable) {
        val timeToFailure = Duration.ofNanos(System.nanoTime() - firstSentByteInstant.get())
        if (sendingFailure == null) {
            sendingFailure = throwable
            eventsLogger?.warn("${eventPrefix}.sending.failed", arrayOf(timeToFailure, throwable), tags = eventTags)
            meterRegistry?.counter("${meterPrefix}-sending-failure", metersTags)?.increment()
        }
    }

    override fun recordSentRequestFailure(throwable: Throwable) {
        eventsLogger?.warn("${eventPrefix}.sending-request-failure", tags = eventTags)
        meterRegistry?.counter("${meterPrefix}-sending-request-failure", metersTags)?.report {
            display("\u2716 %,.0f req", ReportMessageSeverity.ERROR, row = 2, column = 5, Counter::count)
        }?.increment()
    }

    override fun recordReceivingData() {
        meters.timeToFirstByte = Duration.ofNanos(System.nanoTime() - firstSentByteInstant.get())
        eventsLogger?.debug("${eventPrefix}.receiving", meters.timeToFirstByte, tags = eventTags)
        meterRegistry?.timer("${meterPrefix}-receiving", metersTags)?.report {
            display(
                "\u2198 1st byte mean: %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 3,
                column = 0
            ) { this.mean(TimeUnit.MILLISECONDS) }
            display(
                "max: %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 3,
                column = 1
            ) { this.max(TimeUnit.MILLISECONDS) }
        }?.record(meters.timeToFirstByte!!)
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
        meterRegistry?.timer("${meterPrefix}-received-response", metersTags)?.report {
            display(
                "last byte mean: %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 3,
                column = 2
            ) { this.mean(TimeUnit.MILLISECONDS) }
            display(
                "max: %,.3f ms",
                severity = ReportMessageSeverity.INFO,
                row = 3,
                column = 3
            ) { this.max(TimeUnit.MILLISECONDS) }
        }?.record(meters.timeToLastByte!!)
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
            meterRegistry?.counter("${meterPrefix}-receiving-failure", metersTags)?.increment()
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
