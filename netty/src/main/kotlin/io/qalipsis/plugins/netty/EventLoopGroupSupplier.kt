package io.qalipsis.plugins.netty

import io.netty.channel.EventLoopGroup

/**
 * Special service in charge of providing the pre-configured [EventLoopGroup].
 * For now, only a default unconfigurable instance is supported.
 *
 * @author Eric Jessé
 */
interface EventLoopGroupSupplier {

    /**
     * Provides the default instance of [EventLoopGroup]
     */
    fun getGroup(): EventLoopGroup

}