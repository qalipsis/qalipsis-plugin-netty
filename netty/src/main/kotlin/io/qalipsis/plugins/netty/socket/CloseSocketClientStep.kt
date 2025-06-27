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
import io.qalipsis.api.context.StepName
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.api.steps.ErrorProcessingStep

/**
 * Superclass to close a connection that was created in an earlier step and kept open.
 *
 * @author Eric Jessé
 */
internal abstract class CloseSocketClientStep<I>(
    id: StepName,
    private val connectionOwner: SocketClientStep<*, *, *, *>
) : AbstractStep<I, I>(id, null), ErrorProcessingStep<I, I> {

    override suspend fun execute(context: StepContext<I, I>) {
        if (context.hasInput) {
            log.trace { "Forwarding the input to the output" }
            val input = context.receive()
            context.send(input)
            log.trace { "The input was forwarded to the output" }
        }
    }

    override suspend fun discard(minionIds: Collection<MinionId>) {
        log.debug { "Closing the connection" }
        try {
            minionIds.forEach {
                kotlin.runCatching {
                    connectionOwner.close(it)
                }
            }
        } catch (e: Exception) {
            log.trace(e) { e.message }
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()

    }
}
