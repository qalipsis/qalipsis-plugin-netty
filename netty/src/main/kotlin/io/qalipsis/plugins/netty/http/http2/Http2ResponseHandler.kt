package io.qalipsis.plugins.netty.http.http2

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.HttpResponse
import io.netty.util.ReferenceCountUtil
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.plugins.netty.http.client.monitoring.HttpStepContextBasedSocketMonitoringCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Handler for responses for HTTP 2.0.
 *
 * @author Eric Jessé
 */
internal class Http2ResponseHandler(
    private val responseSlot: ImmutableSlot<Result<HttpResponse>>,
    private val monitoringCollector: HttpStepContextBasedSocketMonitoringCollector,
    private val ioCoroutineScope: CoroutineScope
) : SimpleChannelInboundHandler<HttpResponse>() {

    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpResponse) {
        log.trace { "Received a HTTP/2 response: $msg" }
        ReferenceCountUtil.touch(msg)
        ReferenceCountUtil.retain(msg)

        monitoringCollector.recordReceptionComplete()
        monitoringCollector.recordHttpStatus(msg.status())
        ioCoroutineScope.launch {
            responseSlot.set(Result.success(msg))
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        log.trace(cause) { "An exception occurred while processing the HTTP 2.0 response: ${cause.message}" }
        ioCoroutineScope.launch {
            responseSlot.set(Result.failure(cause))
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()

    }
}
