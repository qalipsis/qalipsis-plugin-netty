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

package io.qalipsis.plugins.netty.udp.server

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBufUtil.getBytes
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.DatagramPacket
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.Server
import io.qalipsis.plugins.netty.ServerUtils
import java.net.InetSocketAddress


/**
 * Instance of a local UDP server for test purpose.
 *
 * @author Eric Jessé
 */
open class UdpServer internal constructor(
    override val port: Int,
    private val bootstrap: Bootstrap,
    private val eventGroups: Collection<EventLoopGroup>
) : Server {

    private var channelFuture: ChannelFuture? = null

    override fun start() {
        if (channelFuture == null) {
            channelFuture = bootstrap.bind().sync()
        }
    }

    override fun stop() {
        channelFuture?.let { chFuture ->
            try {
                chFuture.channel().close().sync()
            } finally {
                eventGroups.forEach { group -> group.shutdownGracefully() }
            }
            channelFuture = null
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()

        /**
         * Build a new TCP server. The server is not started yet once built. You can use it either as a JUnit extension or start and stop manually.
         *
         * @param host the host name to listen, or null (default) or all have to be read.
         * @param port the port to use, or null (default) to use a random available one.
         * @param enableTls enables TLS on the server. False by default.
         * @param tlsProtocols list of TLS protocols to support, by default all the ones supported by Netty.
         * @param handler conversion operation of the received payload, default is a simple echo.
         */
        @JvmStatic
        fun new(host: String? = null, port: Int? = null, handler: (ByteArray) -> ByteArray = { it }): Server {
            val bossGroup = NativeTransportUtils.getEventLoopGroup()

            val inetSocketAddress = if (host.isNullOrBlank()) {
                if (port != null) {
                    InetSocketAddress(port)
                } else {
                    InetSocketAddress(ServerUtils.availableTcpPort())
                }
            } else {
                if (port != null) {
                    InetSocketAddress(host, port)
                } else {
                    InetSocketAddress(host, ServerUtils.availableTcpPort())
                }
            }

            val bootstrap = Bootstrap()
                .group(bossGroup)
                .channel(NativeTransportUtils.datagramChannelClass)
                .localAddress(inetSocketAddress)
                .option(ChannelOption.SO_BROADCAST, true)
                .option(ChannelOption.AUTO_READ, true)
                .handler(object : ChannelInitializer<DatagramChannel>() {

                    override fun initChannel(ch: DatagramChannel) {
                        val pipeline = ch.pipeline()
                        pipeline.addLast("request", object : SimpleChannelInboundHandler<DatagramPacket>() {

                            override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                                log.error(cause) { cause.message }
                            }

                            override fun channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket) {
                                val input = getBytes(msg.content())
                                val output = DatagramPacket(
                                    Unpooled.wrappedBuffer(handler(input)), msg.sender(),
                                    msg.recipient()
                                )
                                ctx.writeAndFlush(output).addListener {
                                    if (!it.isSuccess) {
                                        log.error(it.cause()) { it.cause().message }
                                    }
                                }
                            }

                        })
                    }
                })
            return UdpServer(inetSocketAddress.port, bootstrap, listOf(bossGroup))
        }
    }

}
