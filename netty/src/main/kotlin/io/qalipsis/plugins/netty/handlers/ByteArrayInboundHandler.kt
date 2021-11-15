package io.qalipsis.plugins.netty.handlers

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.ImmutableSlot

/**
 * Handler for responses as [ByteArray]s, suitable for low-level clients (TCP, UDP).
 *
 * @author Eric Jessé
 */
internal class ByteArrayInboundHandler(
    private val response: ImmutableSlot<Result<ByteArray>>
) : SimpleChannelInboundHandler<ByteArray>() {

    private var startedRead = false

    private var received = ByteArray(0)

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteArray?) {
        startedRead = true
        if (msg != null) {
            log.trace { "Reading data" }
            received += msg
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        if (startedRead) {
            log.trace { "Read is complete" }
            response.offer(Result.success(received))
        } else {
            // The read complete is also called when establishing the TLS handshake, but should be ignored here.
            log.trace { "Received read complete while no data was read yet" }
        }
        super.channelReadComplete(ctx)
    }

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        super.handlerAdded(ctx)
        ctx.read()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        response.offer(Result.failure(cause))
    }

    private companion object {

        @JvmStatic
        val log = logger()
    }
}
