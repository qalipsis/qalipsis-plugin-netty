/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.plugins.netty.http.http1

import io.netty.channel.Channel
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder
import io.netty.handler.stream.ChunkedWriteHandler
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.plugins.netty.http.HttpPipelineNames
import io.qalipsis.plugins.netty.http.spec.HttpVersion
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.RequestWriter
import kotlinx.coroutines.runBlocking
import java.net.URI

internal open class Http1RequestWriter(
    request: Any,
    private val responseSlot: ImmutableSlot<Result<HttpResponse>>,
    private val monitoringCollector: StepContextBasedSocketMonitoringCollector,
) : RequestWriter {

    private val encoder: HttpPostRequestEncoder? = request as? HttpPostRequestEncoder

    protected val nettyRequest = encoder?.finalizeRequest() ?: request as HttpRequest

    protected open val version = HttpVersion.HTTP_1_1

    override fun write(channel: Channel) {
        configuresMonitoring(nettyRequest)

        var removeChunkedRequestHandler = false
        val requestToFlush: Any = if (encoder?.isChunked == true) {
            removeChunkedRequestHandler = true
            channel.pipeline().addLast(HttpPipelineNames.CHUNKED_REQUEST_HANDLER, ChunkedWriteHandler())
            channel.write(nettyRequest)
            encoder
        } else {
            nettyRequest
        }

        monitoringCollector.recordSendingRequest()
        channel.writeAndFlush(requestToFlush).addListener {
            if (removeChunkedRequestHandler) {
                channel.pipeline().remove(HttpPipelineNames.CHUNKED_REQUEST_HANDLER)
            }
            if (!it.isSuccess) {
                monitoringCollector.recordSentRequestFailure(it.cause())
                if (responseSlot.isEmpty()) {
                    runBlocking {
                        responseSlot.set(Result.failure(it.cause()))
                    }
                }
                log.trace { "The request could not be sent: ${it.cause().message}" }
            } else {
                monitoringCollector.recordSentRequestSuccess()
                log.trace { "The request was successfully sent" }
            }
        }
    }

    /**
     * Configures the monitoring with the exact definition of the request.
     */
    private fun configuresMonitoring(request: HttpRequest) {
        URI.create(request.uri()).also { requestUri ->
            monitoringCollector.setTags(
                "protocol" to version.protocol,
                "method" to "${request.method()}",
                "scheme" to requestUri.scheme,
                "host" to requestUri.host,
                "port" to "${requestUri.port}",
                "path" to requestUri.path
            )
        }
    }

    private companion object {

        @JvmStatic
        val log = logger()
    }
}
