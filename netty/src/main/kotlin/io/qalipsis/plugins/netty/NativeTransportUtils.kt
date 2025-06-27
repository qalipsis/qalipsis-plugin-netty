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
import io.netty.channel.epoll.EpollDatagramChannel
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.epoll.EpollSocketChannel
import io.netty.channel.kqueue.KQueueDatagramChannel
import io.netty.channel.kqueue.KQueueEventLoopGroup
import io.netty.channel.kqueue.KQueueServerSocketChannel
import io.netty.channel.kqueue.KQueueSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel

/**
 * Provides the non-blocking components for the Netty channel
 *
 * @author Eric Jessé
 */
internal interface NativeTransportProvider {

    /**
     * Creates a new [EventLoopGroup] with the provided number of threads.
     */
    fun getEventLoopGroup(size: Int = 0): EventLoopGroup

    /**
     * Class to use to implement a [SocketChannel].
     */
    val socketChannelClass: Class<out SocketChannel>

    /**
     * Class to use to implement a [ServerSocketChannel].
     */
    val serverSocketChannelClass: Class<out ServerSocketChannel>

    /**
     * Class to use to implement a [DatagramChannel].
     */
    val datagramChannelClass: Class<out DatagramChannel>
}

/**
 * Object providing native transport components if possible, or the Netty NIO by default.
 * Read more [here](https://netty.io/wiki/native-transports.html).
 *
 * @author Eric Jessé
 */
object NativeTransportUtils : NativeTransportProvider {

    private val delegate: NativeTransportProvider

    init {
        delegate = if (System.getProperty("os.arch").contains("64")) {
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("linux")) {
                LinuxNativeTransportProvider()
            } else if (os.contains("mac") && System.getProperty("os.arch") != "aarch64") {
                MacX86NativeTransportProvider()
            } else {
                DefaultTransportProvider()
            }
        } else {
            DefaultTransportProvider()
        }
    }

    override fun getEventLoopGroup(size: Int) = delegate.getEventLoopGroup(size)

    override val socketChannelClass: Class<out SocketChannel>
        get() = delegate.socketChannelClass

    override val serverSocketChannelClass: Class<out ServerSocketChannel>
        get() = delegate.serverSocketChannelClass

    override val datagramChannelClass: Class<out DatagramChannel>
        get() = delegate.datagramChannelClass

    /**
     * Implementation of [NativeTransportProvider] used when the running system is under Linux x86 64 bits.
     */
    private class LinuxNativeTransportProvider : NativeTransportProvider {

        override fun getEventLoopGroup(size: Int): EventLoopGroup {
            return EpollEventLoopGroup(size)
        }

        override val serverSocketChannelClass: Class<out ServerSocketChannel>
            get() = EpollServerSocketChannel::class.java

        override val socketChannelClass: Class<out SocketChannel>
            get() = EpollSocketChannel::class.java

        override val datagramChannelClass: Class<out DatagramChannel>
            get() = EpollDatagramChannel::class.java

    }

    /**
     * Implementation of [NativeTransportProvider] used when the running system is under Mac OS x86 64 bits.
     */
    private class MacX86NativeTransportProvider : NativeTransportProvider {

        override fun getEventLoopGroup(size: Int): EventLoopGroup {
            return KQueueEventLoopGroup(size)
        }

        override val serverSocketChannelClass: Class<out ServerSocketChannel>
            get() = KQueueServerSocketChannel::class.java

        override val socketChannelClass: Class<out SocketChannel>
            get() = KQueueSocketChannel::class.java

        override val datagramChannelClass: Class<out DatagramChannel>
            get() = KQueueDatagramChannel::class.java

    }

    /**
     * Default implementation of [NativeTransportProvider], using the Netty NIO library.
     */
    private class DefaultTransportProvider : NativeTransportProvider {

        override fun getEventLoopGroup(size: Int): EventLoopGroup {
            return NioEventLoopGroup(size)
        }

        override val serverSocketChannelClass: Class<out ServerSocketChannel>
            get() = NioServerSocketChannel::class.java

        override val socketChannelClass: Class<out SocketChannel>
            get() = NioSocketChannel::class.java


        override val datagramChannelClass: Class<out DatagramChannel>
            get() = NioDatagramChannel::class.java
    }
}