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

import assertk.all
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isNotSameAs
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

internal class EventLoopGroupSupplierImplTest {

    val eventLoopGroupSupplier = EventLoopGroupSupplierImpl()

    @Test
    internal fun `should provide a new event loop group that shuts down only after latest usage`() {
        // when
        val loopGroup = eventLoopGroupSupplier.getGroup()

        // then
        assertThat(loopGroup).all {
            transform("isShuttingDown") { it.isShuttingDown }.isFalse()
            transform("isShutdown") { it.isShutdown }.isFalse()
            transform("isTerminated") { it.isTerminated }.isFalse()
        }

        // when
        val newLoopGroup = eventLoopGroupSupplier.getGroup()

        // then
        assertThat(newLoopGroup).all {
            isSameAs(loopGroup)
            transform("isShuttingDown") { it.isShuttingDown }.isFalse()
            transform("isShutdown") { it.isShutdown }.isFalse()
            transform("isTerminated") { it.isTerminated }.isFalse()
        }

        // when
        // Should not shutdown until all the client did not shut it down.
        loopGroup.shutdownGracefully().await()

        // then
        assertThat(newLoopGroup).all {
            isSameAs(loopGroup)
            transform("isShuttingDown") { it.isShuttingDown }.isFalse()
            transform("isShutdown") { it.isShutdown }.isFalse()
            transform("isTerminated") { it.isTerminated }.isFalse()
        }

        // when
        loopGroup.shutdownGracefully().await()

        // then
        assertThat(newLoopGroup).all {
            isSameAs(loopGroup)
            transform("isShutdown") { it.isShutdown }.isTrue()
            transform("isTerminated") { it.isTerminated }.isTrue()
        }

        // when
        val anotherLoopGroup = eventLoopGroupSupplier.getGroup()

        // then
        assertThat(anotherLoopGroup).all {
            isNotSameAs(loopGroup)
            transform("isShuttingDown") { it.isShuttingDown }.isFalse()
            transform("isShutdown") { it.isShutdown }.isFalse()
            transform("isTerminated") { it.isTerminated }.isFalse()
        }
        anotherLoopGroup.shutdownGracefully().await()
    }
}