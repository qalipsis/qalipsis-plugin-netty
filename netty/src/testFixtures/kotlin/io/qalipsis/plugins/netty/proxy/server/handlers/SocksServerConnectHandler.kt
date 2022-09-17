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
package io.qalipsis.plugins.netty.proxy.server.handlers

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.example.socksproxy.DirectClientHandler
import io.netty.example.socksproxy.RelayHandler
import io.netty.example.socksproxy.SocksServerUtils
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.FutureListener
import io.qalipsis.plugins.netty.NativeTransportUtils

@ChannelHandler.Sharable
internal class SocksServerConnectHandler : SimpleChannelInboundHandler<SocksMessage?>() {

    private val b = Bootstrap()

    public override fun channelRead0(ctx: ChannelHandlerContext, message: SocksMessage?) {
        when (message) {
            is Socks4CommandRequest -> {
                val promise = ctx.executor().newPromise<Channel>()
                promise.addListener(
                    object : FutureListener<Channel> {

                        override fun operationComplete(future: Future<Channel>) {
                            val outboundChannel = future.now
                            if (future.isSuccess) {
                                val responseFuture = ctx.channel().writeAndFlush(
                                    DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS)
                                )
                                responseFuture.addListener(ChannelFutureListener {
                                    ctx.pipeline().remove(this@SocksServerConnectHandler)
                                    outboundChannel.pipeline().addLast(RelayHandler(ctx.channel()))
                                    ctx.pipeline().addLast(RelayHandler(outboundChannel))
                                })
                            } else {
                                ctx.channel().writeAndFlush(
                                    DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED)
                                )
                                SocksServerUtils.closeOnFlush(ctx.channel())
                            }
                        }
                    })
                val inboundChannel = ctx.channel()
                b.group(inboundChannel.eventLoop())
                    .channel(NativeTransportUtils.socketChannelClass)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(DirectClientHandler(promise))
                b.connect(message.dstAddr(), message.dstPort()).addListener(ChannelFutureListener { future ->
                    if (future.isSuccess) {
                        // Connection established use handler provided results
                    } else {
                        // Close the connection if the connection attempt has failed.
                        ctx.channel().writeAndFlush(
                            DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED)
                        )
                        SocksServerUtils.closeOnFlush(ctx.channel())
                    }
                })
            }
            is Socks5CommandRequest -> {
                val promise = ctx.executor().newPromise<Channel>()
                promise.addListener(object : FutureListener<Channel> {

                    override fun operationComplete(future: Future<Channel>) {
                        val outboundChannel = future.now
                        if (future.isSuccess) {
                            val responseFuture = ctx.channel().writeAndFlush(
                                DefaultSocks5CommandResponse(
                                    Socks5CommandStatus.SUCCESS,
                                    message.dstAddrType(),
                                    message.dstAddr(),
                                    message.dstPort()
                                )
                            )
                            responseFuture.addListener(ChannelFutureListener {
                                ctx.pipeline().remove(this@SocksServerConnectHandler)
                                outboundChannel.pipeline().addLast(RelayHandler(ctx.channel()))
                                ctx.pipeline().addLast(RelayHandler(outboundChannel))
                            })
                        } else {
                            ctx.channel().writeAndFlush(
                                DefaultSocks5CommandResponse(
                                    Socks5CommandStatus.FAILURE, message.dstAddrType()
                                )
                            )
                            SocksServerUtils.closeOnFlush(ctx.channel())
                        }
                    }
                })
                val inboundChannel = ctx.channel()
                b.group(inboundChannel.eventLoop())
                    .channel(NativeTransportUtils.socketChannelClass)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(DirectClientHandler(promise))
                b.connect(message.dstAddr(), message.dstPort()).addListener(ChannelFutureListener { future ->
                    if (future.isSuccess) {
                        // Connection established use handler provided results
                    } else {
                        // Close the connection if the connection attempt has failed.
                        ctx.channel().writeAndFlush(
                            DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, message.dstAddrType())
                        )
                        SocksServerUtils.closeOnFlush(ctx.channel())
                    }
                })
            }
            else -> {
                ctx.close()
            }
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        SocksServerUtils.closeOnFlush(ctx.channel())
    }
}