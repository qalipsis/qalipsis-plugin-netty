package io.qalipsis.plugins.netty.proxy.server.handlers

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler
import java.util.concurrent.atomic.AtomicInteger

class SocksServerInitializer(
    private val requestCounter: AtomicInteger
) : ChannelInitializer<SocketChannel>() {

    public override fun initChannel(ch: SocketChannel) {
        ch.pipeline().addLast(SocksPortUnificationServerHandler(), SocksServerHandler(requestCounter))
    }
}
