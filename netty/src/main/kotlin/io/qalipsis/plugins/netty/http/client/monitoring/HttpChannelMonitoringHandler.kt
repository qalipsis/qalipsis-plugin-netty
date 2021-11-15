package io.qalipsis.plugins.netty.http.client.monitoring

import io.netty.channel.ChannelHandlerContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.netty.handlers.monitoring.ChannelMonitoringHandler
import io.qalipsis.plugins.netty.monitoring.MonitoringCollector

/**
 * Channel handler to record the activity of an HTTP channel.
 *
 * @author Eric Jessé
 */
internal class HttpChannelMonitoringHandler(
    monitoringCollector: MonitoringCollector
) : ChannelMonitoringHandler(monitoringCollector) {

    /**
     * The completion should be recorded by the response handler.
     */
    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.fireChannelReadComplete()
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
