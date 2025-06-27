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

package io.qalipsis.plugins.netty.tcp.server

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.util.SelfSignedCertificate
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.netty.NativeTransportUtils
import io.qalipsis.plugins.netty.Server
import io.qalipsis.plugins.netty.ServerUtils
import java.net.InetSocketAddress


/**
 * Instance of a local TCP server for test purpose.
 *
 * @author Eric Jessé
 */
open class TcpServer internal constructor(
    override val port: Int,
    private val bootstrap: ServerBootstrap,
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
        fun new(
            host: String? = null, port: Int? = null, enableTls: Boolean = false,
            tlsProtocols: Array<String> = arrayOf(),
            handler: (ByteArray) -> ByteArray = { it }
        ): Server {
            val bossGroup = NativeTransportUtils.getEventLoopGroup()
            val workerGroup = NativeTransportUtils.getEventLoopGroup()

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

            val sslSelfSignedCertificate = if (enableTls) SelfSignedCertificate() else null
            val sslContext = if (enableTls) SslContextBuilder.forServer(
                sslSelfSignedCertificate!!.certificate(),
                sslSelfSignedCertificate.privateKey()
            ).build() else null

            val bootstrap = ServerBootstrap().group(bossGroup, workerGroup)
                .channel(NativeTransportUtils.serverSocketChannelClass)
                .localAddress(inetSocketAddress)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.AUTO_READ, true)
                .childHandler(object : ChannelInitializer<SocketChannel>() {

                    override fun initChannel(ch: SocketChannel) {
                        val pipeline = ch.pipeline()
                        if (enableTls) {
                            val sslEngine = sslContext!!.newEngine(ch.alloc())
                            if (tlsProtocols.isNotEmpty()) {
                                sslEngine.enabledProtocols = tlsProtocols
                            } else {
                                sslEngine.enabledProtocols = sslEngine.supportedProtocols
                            }
                            pipeline.addFirst("ssl", SslHandler(sslEngine))
                        }
                        pipeline.addLast("request", object : SimpleChannelInboundHandler<ByteBuf>() {

                            override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                                log.error(cause) { cause.message }
                            }

                            override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
                                val input = ByteBufUtil.getBytes(msg)
                                val output = Unpooled.wrappedBuffer(handler(input))
                                ctx.writeAndFlush(output).addListener {
                                    if (!it.isSuccess) {
                                        log.error(it.cause()) { it.cause().message }
                                    }
                                }
                            }

                        })
                    }
                })

            return TcpServer(
                inetSocketAddress.port, bootstrap,
                listOf(bossGroup, workerGroup)
            )
        }
    }

}
