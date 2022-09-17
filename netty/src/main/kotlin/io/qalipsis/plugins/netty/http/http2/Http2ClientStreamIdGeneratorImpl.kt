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
