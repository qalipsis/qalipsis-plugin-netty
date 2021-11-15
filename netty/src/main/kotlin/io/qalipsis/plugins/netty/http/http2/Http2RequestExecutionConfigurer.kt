package io.qalipsis.plugins.netty.http.http2

import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.http.HttpResponse
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.plugins.netty.http.HttpPipelineNames.CHANNEL_MONITORING_HANDLER
import io.qalipsis.plugins.netty.http.HttpPipelineNames.INBOUND_HANDLER
import io.qalipsis.plugins.netty.http.client.HttpRequestExecutionConfigurer
import io.qalipsis.plugins.netty.http.client.monitoring.HttpChannelMonitoringHandler
import io.qalipsis.plugins.netty.http.client.monitoring.HttpStepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.http.request.HttpRequest
import io.qalipsis.plugins.netty.http.request.InternalHttpRequest
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.RequestWriter
import kotlinx.coroutines.CoroutineScope

/**
 * Implementation of [HttpRequestExecutionConfigurer] for HTTP/2.
 *
 * @author Eric Jessé
 */
internal class Http2RequestExecutionConfigurer(
    private val clientConfiguration: HttpClientConfiguration,
    private val pipeline: ChannelPipeline,
    private val ioCoroutineScope: CoroutineScope
) : HttpRequestExecutionConfigurer {

    private var streamIdGenerator = Http2ClientStreamIdGeneratorImpl()

    override fun configure(
        request: HttpRequest<*>,
        monitoringCollector: StepContextBasedSocketMonitoringCollector,
        responseSlot: ImmutableSlot<Result<HttpResponse>>
    ): RequestWriter {
        pipeline.addFirst(CHANNEL_MONITORING_HANDLER, HttpChannelMonitoringHandler(monitoringCollector))
        pipeline.addLast(
            INBOUND_HANDLER,
            Http2ResponseHandler(
                responseSlot,
                monitoringCollector as HttpStepContextBasedSocketMonitoringCollector,
                ioCoroutineScope
            )
        )

        return Http2RequestWriter(
            (request as InternalHttpRequest<*, *>).toNettyRequest(clientConfiguration),
            responseSlot,
            monitoringCollector,
            if (clientConfiguration.isSecure) "https" else "http",
            streamIdGenerator,
            ioCoroutineScope
        )
    }

}
