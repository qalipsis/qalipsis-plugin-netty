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
