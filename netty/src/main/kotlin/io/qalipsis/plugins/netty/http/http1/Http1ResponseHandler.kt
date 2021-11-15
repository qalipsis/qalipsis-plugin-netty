package io.qalipsis.plugins.netty.http.http1

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpResponse
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.plugins.netty.http.client.monitoring.HttpStepContextBasedSocketMonitoringCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Handler for responses for HTTP 1.1.
 *
 * @author Eric Jessé
 */
internal class Http1ResponseHandler(
    private val responseSlot: ImmutableSlot<Result<HttpResponse>>,
    private val monitoringCollector: HttpStepContextBasedSocketMonitoringCollector,
    private val ioCoroutineScope: CoroutineScope
) : SimpleChannelInboundHandler<FullHttpResponse>() {

    override fun channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse) {
        monitoringCollector.recordReceptionComplete()
        monitoringCollector.recordHttpStatus(msg.status())
        msg.touch()
        msg.retain()
        ioCoroutineScope.launch {
            responseSlot.offer(Result.success(msg))
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        log.trace(cause) { "An exception occurred while processing the HTTP 1.1 response: ${cause.message}" }
        ioCoroutineScope.launch {
            responseSlot.offer(Result.failure(cause))
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()

    }
}
