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

package io.qalipsis.plugins.netty.tcp

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.plugins.netty.ByteArrayRequestBuilder
import io.qalipsis.plugins.netty.ByteArrayRequestBuilderImpl
import io.qalipsis.plugins.netty.socket.QuerySocketClientStep
import io.qalipsis.plugins.netty.socket.SocketClientStep

/**
 * Step to perform a TCP operations onto a server, reusing the same connection from a past action.
 *
 * @author Eric Jessé
 */
internal class QueryTcpClientStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    connectionOwner: TcpClientStep<*, *>,
    requestFactory: suspend ByteArrayRequestBuilder.(StepContext<*, *>, I) -> ByteArray,
    eventsLogger: EventsLogger?,
    meterRegistry: CampaignMeterRegistry?
) : QuerySocketClientStep<I, ByteArray, ByteArray, ByteArray, ByteArrayRequestBuilder, SocketClientStep<*, ByteArray, ByteArray, *>>(
    id,
    retryPolicy,
    connectionOwner,
    "with-tcp",
    ByteArrayRequestBuilderImpl,
    requestFactory,
    eventsLogger,
    meterRegistry
)
