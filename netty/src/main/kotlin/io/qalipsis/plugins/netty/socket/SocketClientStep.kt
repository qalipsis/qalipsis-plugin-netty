package io.qalipsis.plugins.netty.socket

import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.Step
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector

/**
 * General interface for the steps using a socket.
 *
 * @author Eric Jessé
 */
internal interface SocketClientStep<I, REQ : Any, RES : Any, O> : Step<I, O> {

    /**
     * Executes a request and reads the response from the server.
     * If a client exists and is open, it is reused, otherwise a new one is created.
     */
    suspend fun <IN> execute(
        monitoringCollector: StepContextBasedSocketMonitoringCollector,
        context: StepContext<*, *>,
        input: IN,
        request: REQ
    ): RES

    /**
     * Keeps the connections open, waiting for a later step to close them manually.
     */
    fun keepOpen() = Unit

    /**
     * Closes the connection if not yet done.
     */
    suspend fun close(minionId: MinionId) = Unit

    /**
     * Add a further step as a user of the same connection.
     */
    fun addUsage(count: Int = 1) = Unit
}
