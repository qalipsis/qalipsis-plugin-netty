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

package io.qalipsis.plugins.netty.http.http2

import io.netty.channel.Channel
import io.netty.handler.codec.http.HttpResponse
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.plugins.netty.http.client.StreamIdGenerator
import io.qalipsis.plugins.netty.http.http1.Http1RequestWriter
import io.qalipsis.plugins.netty.http.spec.HttpVersion
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector

internal class Http2RequestWriter(
    request: Any,
    responseSlot: ImmutableSlot<Result<HttpResponse>>,
    monitoringCollector: StepContextBasedSocketMonitoringCollector,
    private val scheme: String,
    private val streamIdGenerator: StreamIdGenerator<Int>
) : Http1RequestWriter(request, responseSlot, monitoringCollector) {

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
