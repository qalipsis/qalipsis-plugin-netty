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

package io.qalipsis.plugins.netty.http.http2

import io.qalipsis.plugins.netty.http.client.StreamIdGenerator
import java.util.concurrent.atomic.AtomicInteger

/**
 * Generates Stream IDs for HTTP/2 according to [https://tools.ietf.org/html/rfc7540#section-5.1.1][https://tools.ietf.org/html/rfc7540#section-5.1.1].
 * Only odd values should be generated on client side.
 *
 * @author Eric Jessé
 */
internal class Http2ClientStreamIdGeneratorImpl(initValue: Int = 1) : StreamIdGenerator<Int> {

    private val counter = AtomicInteger()

    init {
        val lastUsedValue = if (initValue % 2 == 0) initValue - 1 else initValue
        counter.set(lastUsedValue.coerceAtLeast(1))
    }

    override fun next(): Int {
        val v = counter.addAndGet(2)
        check(v > 0) { "No more identifier available" }
        return v
    }
}
