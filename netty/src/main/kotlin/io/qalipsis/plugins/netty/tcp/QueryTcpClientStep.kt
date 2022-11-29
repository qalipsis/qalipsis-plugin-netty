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

package io.qalipsis.plugins.netty.tcp

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.plugins.netty.socket.QuerySocketClientStep
import io.qalipsis.plugins.netty.socket.SocketClientStep
import kotlin.coroutines.CoroutineContext

/**
 * Step to perform a TCP operations onto a server, reusing the same connection from a past action.
 *
 * @author Eric Jessé
 */
internal class QueryTcpClientStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    ioCoroutineContext: CoroutineContext,
    connectionOwner: TcpClientStep<*, *>,
    requestFactory: suspend (StepContext<*, *>, I) -> ByteArray,
    eventsLogger: EventsLogger?,
    meterRegistry: CampaignMeterRegistry?
) : QuerySocketClientStep<I, ByteArray, ByteArray, ByteArray, SocketClientStep<*, ByteArray, ByteArray, *>>(
    id,
    retryPolicy,
    ioCoroutineContext,
    connectionOwner,
    "with-tcp",
    requestFactory,
    eventsLogger,
    meterRegistry
)
