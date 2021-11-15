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