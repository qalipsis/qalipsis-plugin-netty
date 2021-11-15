package io.qalipsis.plugins.netty.http.client

import io.netty.handler.codec.http.HttpResponse
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.RequestWriter

/**
 * Service in charge of configuring the different components (Netty and QALIPSIS) when a HTTP request is being executed.
 *
 * @author Eric Jessé
 */
internal interface HttpRequestExecutionConfigurer {

    fun configure(
        request: io.qalipsis.plugins.netty.http.request.HttpRequest<*>,
        monitoringCollector: StepContextBasedSocketMonitoringCollector,
        responseSlot: ImmutableSlot<Result<HttpResponse>>
    ): RequestWriter

}
