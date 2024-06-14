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
