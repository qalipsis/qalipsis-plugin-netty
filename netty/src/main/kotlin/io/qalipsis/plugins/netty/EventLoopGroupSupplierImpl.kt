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

import io.netty.channel.EventLoopGroup
import io.netty.util.NettyRuntime
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.ImmediateEventExecutor
import io.netty.util.internal.SystemPropertyUtil
import io.qalipsis.api.annotations.PluginComponent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * Default implementation of a [EventLoopGroupSupplier].
 *
 * @author Eric Jessé
 */
@PluginComponent
internal class EventLoopGroupSupplierImpl : EventLoopGroupSupplier {

    private val loopGroups = ConcurrentHashMap<String, SharedEventLoopGroup>()

    override fun getGroup(): EventLoopGroup {
        return loopGroups.compute(
            DEFAULT_LOOP_GROUP
        ) { key, group ->
            group?.apply { addUsage() } ?: SharedEventLoopGroup(
                NativeTransportUtils.getEventLoopGroup(
                    DEFAULT_EVENT_LOOP_THREADS
                )
            ) { loopGroups.remove(key) }
        }!!
    }

    private companion object {

        val DEFAULT_EVENT_LOOP_THREADS =
            max(1, SystemPropertyUtil.getInt("io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2))

        const val DEFAULT_LOOP_GROUP = "_default_"

    }

    private class SharedEventLoopGroup(
        private val delegate: EventLoopGroup,
        private val onClose: SharedEventLoopGroup.() -> Unit
    ) : EventLoopGroup by delegate {

        private val usages = AtomicInteger(1)

        fun addUsage() {
            usages.incrementAndGet()
        }

        @Deprecated("Use shutdownGracefully()")
        override fun shutdown() {
            if (usages.decrementAndGet() == 0) {
                this.onClose()
                delegate.shutdown()
            }
        }

        @Deprecated("Use shutdownGracefully()")
        override fun shutdownNow(): MutableList<Runnable> {
            return if (usages.decrementAndGet() == 0) {
                this.onClose()
                delegate.shutdownNow()
            } else {
                mutableListOf()
            }
        }

        override fun shutdownGracefully(): Future<*> {
            return if (usages.decrementAndGet() == 0) {
                this.onClose()
                delegate.shutdownGracefully()
            } else {
                ImmediateEventExecutor.INSTANCE.newSucceededFuture(Unit)
            }
        }

        override fun shutdownGracefully(quietPeriod: Long, timeout: Long, unit: TimeUnit?): Future<*> {
            return if (usages.decrementAndGet() == 0) {
                this.onClose()
                delegate.shutdownGracefully(quietPeriod, timeout, unit)
            } else {
                ImmediateEventExecutor.INSTANCE.newSucceededFuture(Unit)
            }
        }
    }
}