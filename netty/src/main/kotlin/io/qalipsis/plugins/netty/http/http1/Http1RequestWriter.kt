/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
