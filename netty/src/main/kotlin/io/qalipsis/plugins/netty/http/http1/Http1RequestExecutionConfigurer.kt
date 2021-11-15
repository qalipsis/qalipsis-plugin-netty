package io.qalipsis.plugins.netty.http.http1

import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.http.HttpResponse
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.plugins.netty.PipelineHandlerNames
import io.qalipsis.plugins.netty.http.HttpPipelineNames
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
 * Implementation of [HttpRequestExecutionConfigurer] for HTTP/1.1.
 *
 * @author Eric Jessé
 */
internal class Http1RequestExecutionConfigurer(
    private val clientConfiguration: HttpClientConfiguration,
    private val pipeline: ChannelPipeline,
    private val ioCoroutineScope: CoroutineScope
) : HttpRequestExecutionConfigurer {

    override fun configure(
        request: HttpRequest<*>,
        monitoringCollector: StepContextBasedSocketMonitoringCollector,
        responseSlot: ImmutableSlot<Result<HttpResponse>>
    ): RequestWriter {
        pipeline.addBefore(
            PipelineHandlerNames.CLIENT_CODEC,
            HttpPipelineNames.CHANNEL_MONITORING_HANDLER,
            HttpChannelMonitoringHandler(monitoringCollector)
        )
        pipeline.addLast(
            HttpPipelineNames.INBOUND_HANDLER,
            Http1ResponseHandler(
                responseSlot,
                monitoringCollector as HttpStepContextBasedSocketMonitoringCollector,
                ioCoroutineScope
            )
        )

        return Http1RequestWriter(
            (request as InternalHttpRequest<*, *>).toNettyRequest(clientConfiguration),
            responseSlot,
            monitoringCollector,
            ioCoroutineScope
        )
    }
}
