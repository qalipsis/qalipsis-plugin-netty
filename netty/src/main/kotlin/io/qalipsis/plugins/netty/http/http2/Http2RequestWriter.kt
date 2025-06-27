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
