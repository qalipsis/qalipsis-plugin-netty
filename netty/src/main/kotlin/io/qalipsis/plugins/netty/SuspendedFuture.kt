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

package io.qalipsis.plugins.netty

import io.netty.util.concurrent.Future
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.api.sync.SuspendedFuture
import java.time.Duration


/**
 * Creates a [SuspendedFuture] based upon this [Future].
 */
fun <V> Future<V>.asSuspended(): SuspendedFuture<V> = SuspendedFutureForNetty(this)

/**
 * Implementation of [SuspendedFuture] for the [Future].
 */
internal class SuspendedFutureForNetty<V>(future: Future<V>) :
    SuspendedFuture<V> {

    private var result = ImmutableSlot<Result<V>>()

    init {
        future.addListener {
            if (it.isSuccess) {
                @Suppress("UNCHECKED_CAST")
                result.offer(Result.success(it.get() as V))
            } else {
                result.offer(Result.failure(it.cause()))
            }
        }
    }

    override suspend fun get(): V {
        return result.get().getOrThrow()
    }

    override suspend fun get(timeout: Duration): V {
        return result.get(timeout).getOrThrow()
    }
}
