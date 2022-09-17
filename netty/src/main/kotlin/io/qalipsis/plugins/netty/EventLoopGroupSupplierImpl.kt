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