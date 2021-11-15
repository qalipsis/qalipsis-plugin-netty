package io.qalipsis.plugins.netty.http.http1

import io.netty.handler.codec.http.FullHttpMessage
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpUtil

/**
 *
 * Implementations of [HttpObjectAggregator] that forces the aggregation to finish when the last message is identified.
 *
 * @author Eric Jessé
 */
internal class Http1ComposableObjectAggregator(maxContentLength: Int) : HttpObjectAggregator(maxContentLength) {

    override fun finishAggregation(aggregated: FullHttpMessage) {
        // Closes the aggregation on the last message.
        if (!HttpUtil.isContentLengthSet(aggregated) && aggregated.content().readableBytes() > 0) {
            super.finishAggregation(aggregated)
        }
    }
}
