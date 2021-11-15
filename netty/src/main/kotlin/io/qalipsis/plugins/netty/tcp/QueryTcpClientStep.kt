package io.qalipsis.plugins.netty.tcp

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.events.EventsLogger
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
    id: StepId,
    retryPolicy: RetryPolicy?,
    ioCoroutineContext: CoroutineContext,
    connectionOwner: TcpClientStep<*, *>,
    requestFactory: suspend (StepContext<*, *>, I) -> ByteArray,
    eventsLogger: EventsLogger?,
    meterRegistry: MeterRegistry?
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
