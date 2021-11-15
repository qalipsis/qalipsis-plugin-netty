package io.qalipsis.plugins.netty.http.http2

import io.netty.channel.Channel
import io.netty.handler.codec.http.HttpResponse
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.plugins.netty.http.client.StreamIdGenerator
import io.qalipsis.plugins.netty.http.http1.Http1RequestWriter
import io.qalipsis.plugins.netty.http.spec.HttpVersion
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import kotlinx.coroutines.CoroutineScope

internal class Http2RequestWriter(
    request: Any,
    responseSlot: ImmutableSlot<Result<HttpResponse>>,
    monitoringCollector: StepContextBasedSocketMonitoringCollector,
    private val scheme: String,
    private val streamIdGenerator: StreamIdGenerator<Int>,
    ioCoroutineScope: CoroutineScope
) : Http1RequestWriter(request, responseSlot, monitoringCollector, ioCoroutineScope) {

    override val version = HttpVersion.HTTP_2_0

    override fun write(channel: Channel) {
        nettyRequest.headers().add(
            io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(),
            scheme
        )
        nettyRequest.headers().add(
            io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(),
            "${streamIdGenerator.next()}"
        )

        super.write(channel)
    }
}
