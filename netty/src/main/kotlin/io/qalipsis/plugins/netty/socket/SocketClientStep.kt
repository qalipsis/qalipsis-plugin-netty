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
