package io.qalipsis.plugins.netty.socket

import io.qalipsis.plugins.netty.monitoring.MonitoringCollector

internal interface SocketMonitoringCollector : MonitoringCollector {

    val cause: Throwable?
}
