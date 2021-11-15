package io.qalipsis.plugins.netty.monitoring

import java.time.Duration

internal interface MonitoringCollector {

    fun recordConnecting() = Unit

    fun recordConnected(timeToConnect: Duration) = Unit

    fun recordConnectionFailure(timeToFailure: Duration, throwable: Throwable) = Unit

    fun recordTlsHandshakeSuccess(timeToConnect: Duration) = Unit

    fun recordTlsHandshakeFailure(timeToFailure: Duration, throwable: Throwable) = Unit

    fun recordSendingRequest()

    fun recordSendingData(bytesCount: Int)

    fun recordSentDataSuccess(bytesCount: Int)

    fun recordSentDataFailure(throwable: Throwable)

    fun recordSentRequestSuccess()

    fun recordSentRequestFailure(throwable: Throwable)

    fun recordReceivingData()

    fun countReceivedData(bytesCount: Int)

    fun recordReceivingDataFailure(throwable: Throwable)

    fun recordReceptionComplete()
}
