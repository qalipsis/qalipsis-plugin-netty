package io.qalipsis.plugins.netty.monitoring

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.netty.socket.SocketMonitoringCollector
import java.time.Duration

/**
 * Implementation of [MonitoringCollector] to log information relatively to a [io.qalipsis.api.steps.Step].
 *
 * @author Eric Jessé
 */
internal class StepBasedTcpMonitoringCollector(
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: MeterRegistry?,
    context: StepStartStopContext,
    stepQualifier: String
) : SocketMonitoringCollector {

    override var cause: Throwable? = null

    private val eventPrefix = "netty.${stepQualifier}"

    private val metersPrefix = "netty-${stepQualifier}"

    private val eventsTags = context.toEventTags()

    private val metersTags = context.toMetersTags()

    override fun recordConnecting() {
        eventsLogger?.info("${eventPrefix}.connecting", tags = eventsTags)
        meterRegistry?.counter("${metersPrefix}-connecting", metersTags)?.increment()
    }

    override fun recordConnected(timeToConnect: Duration) {
        eventsLogger?.info("${eventPrefix}.connected", timeToConnect, tags = eventsTags)
        meterRegistry?.counter("${metersPrefix}-connected", metersTags)?.increment()
    }

    override fun recordConnectionFailure(timeToFailure: Duration, throwable: Throwable) {
        cause = throwable
        eventsLogger?.warn(
            "${eventPrefix}.connection-failure",
            arrayOf(timeToFailure, throwable),
            tags = eventsTags
        )
        meterRegistry?.counter("${metersPrefix}-connection-failure", metersTags)?.increment()
    }

    override fun recordTlsHandshakeSuccess(timeToConnect: Duration) {
        eventsLogger?.info("${eventPrefix}.tls-connected", timeToConnect, tags = eventsTags)
        meterRegistry?.counter("${metersPrefix}-tls-connected", metersTags)?.increment()
    }

    override fun recordTlsHandshakeFailure(timeToFailure: Duration, throwable: Throwable) {
        cause = throwable
        eventsLogger?.warn(
            "${eventPrefix}.tls-connection-failure",
            arrayOf(timeToFailure, throwable),
            tags = eventsTags
        )
        meterRegistry?.counter("${metersPrefix}-tls-failure", metersTags)?.increment()
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
