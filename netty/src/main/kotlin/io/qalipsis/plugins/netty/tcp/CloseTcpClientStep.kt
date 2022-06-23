package io.qalipsis.plugins.netty.tcp

import io.qalipsis.api.context.StepName
import io.qalipsis.api.steps.ErrorProcessingStep
import io.qalipsis.plugins.netty.socket.CloseSocketClientStep
import kotlin.coroutines.CoroutineContext

/**
 * Step to close a TCP connection that was created in an earlier step and kept open.
 *
 * @author Eric Jessé
 */
internal class CloseTcpClientStep<I>(
    id: StepName,
    ioCoroutineContext: CoroutineContext,
    connectionOwner: TcpClientStep<*, *>,
) : CloseSocketClientStep<I>(id, ioCoroutineContext, connectionOwner), ErrorProcessingStep<I, I>
