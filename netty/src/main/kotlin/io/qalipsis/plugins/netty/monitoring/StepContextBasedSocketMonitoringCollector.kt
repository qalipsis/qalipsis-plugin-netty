package io.qalipsis.plugins.netty.monitoring

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.concurrentList
import io.qalipsis.plugins.netty.socket.SocketMonitoringCollector
import io.qalipsis.plugins.netty.tcp.ConnectionAndRequestResult
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

internal open class StepContextBasedSocketMonitoringCollector(
    private val stepContext: StepContext<*, *>,
    protected val eventsLogger: EventsLogger?,
    protected val meterRegistry: MeterRegistry?,
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

    protected val metersPrefix = "netty-${stepQualifier}"

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
        meterRegistry?.counter("${metersPrefix}-connecting", metersTags)?.increment()
    }

    override fun recordConnected(timeToConnect: Duration) {
        connected = true
        meters.timeToSuccessfulConnect = timeToConnect
        eventsLogger?.info("${eventPrefix}.connected", timeToConnect, tags = eventTags)
        meterRegistry?.counter("${metersPrefix}-connected", metersTags)?.increment()
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
            meterRegistry?.counter("${metersPrefix}-connection-failure", metersTags)?.increment()
        }
    }

    override fun recordTlsHandshakeSuccess(timeToConnect: Duration) {
        connected = true
        meters.timeToSuccessfulTlsConnect = timeToConnect
        eventsLogger?.info("${eventPrefix}.tls-connected", timeToConnect, tags = eventTags)
        meterRegistry?.counter("${metersPrefix}-tls-connected", metersTags)?.increment()
    }

    override fun recordTlsHandshakeFailure(timeToFailure: Duration, throwable: Throwable) {
        if (tlsFailure == null) {
            eventsLogger?.warn(
                "${eventPrefix}.tls-connection-failure",
                arrayOf(timeToFailure, throwable),
                tags = eventTags
            )
            meterRegistry?.counter("${metersPrefix}-tls-failure", metersTags)?.increment()

            connected = false
            meters.timeToFailedTlsConnect = timeToFailure
            tlsFailure = throwable
        }
    }

    override fun recordSendingRequest() {
        eventsLogger?.info("${eventPrefix}.sending-request", tags = eventTags)
        meterRegistry?.counter("${metersPrefix}-sending-request", metersTags)
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
        meterRegistry?.counter("${metersPrefix}-sending-bytes", metersTags)
            ?.increment(bytesCount.toDouble())
        meters.bytesCountToSend += bytesCount
    }

    override fun recordSentDataSuccess(bytesCount: Int) {
        val timeToSent = Duration.ofNanos(System.nanoTime() - sendingInstants.removeAt(0))
        if (bytesCount > 0) {
            eventsLogger?.debug("${eventPrefix}.sent-bytes", arrayOf(timeToSent, bytesCount), tags = eventTags)
        } else {
            eventsLogger?.trace("${eventPrefix}.sent-bytes", arrayOf(timeToSent, bytesCount), tags = eventTags)
        }
        meterRegistry?.counter("${metersPrefix}-sent-bytes", metersTags)
            ?.increment(bytesCount.toDouble())
        meters.sentBytes += bytesCount
    }

    override fun recordSentDataFailure(throwable: Throwable) {
        val timeToFailure = Duration.ofNanos(System.nanoTime() - firstSentByteInstant.get())
        if (sendingFailure == null) {
            sendingFailure = throwable
            eventsLogger?.warn("${eventPrefix}.sending.failed", arrayOf(timeToFailure, throwable), tags = eventTags)
            meterRegistry?.counter("${metersPrefix}-sending-failure", metersTags)?.increment()
        }
    }

    override fun recordSentRequestSuccess() {
        eventsLogger?.info("${eventPrefix}.sent-request", tags = eventTags)
        meterRegistry?.counter("${metersPrefix}-sent-request", metersTags)
    }

    override fun recordSentRequestFailure(throwable: Throwable) {
        eventsLogger?.warn("${eventPrefix}.sending-request-failure", tags = eventTags)
        meterRegistry?.counter("${metersPrefix}-sending-request-failure", metersTags)
    }

    override fun recordReceivingData() {
        meters.timeToFirstByte = Duration.ofNanos(System.nanoTime() - firstSentByteInstant.get())
        eventsLogger?.debug("${eventPrefix}.receiving", meters.timeToFirstByte, tags = eventTags)
        meterRegistry?.counter("${metersPrefix}-receiving", metersTags)?.increment()
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
        meterRegistry?.counter("${metersPrefix}-received-response", metersTags)?.increment()
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
            meterRegistry?.counter("${metersPrefix}-receiving-failure", metersTags)?.increment()
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
