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