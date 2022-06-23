package io.qalipsis.plugins.netty.socket

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.api.steps.ErrorProcessingStep
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Superclass to close a connection that was created in an earlier step and kept open.
 *
 * @author Eric Jessé
 */
internal abstract class CloseSocketClientStep<I>(
    id: StepName,
    private val ioCoroutineContext: CoroutineContext,
    private val connectionOwner: SocketClientStep<*, *, *, *>
) : AbstractStep<I, I>(id, null), ErrorProcessingStep<I, I> {

    override suspend fun execute(context: StepContext<I, I>) {
        if (context.isTail) {
            log.debug { "Closing the connection" }
            try {
                withContext(ioCoroutineContext) {
                    connectionOwner.close(context)
                }
            } catch (e: Exception) {
                log.trace(e) { e.message }
            }
        }

        if (context.hasInput) {
            log.trace { "Forwarding the input to the output" }
            val input = context.receive()
            context.send(input)
            log.trace { "The input was forwarded to the output" }
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()

    }
}
