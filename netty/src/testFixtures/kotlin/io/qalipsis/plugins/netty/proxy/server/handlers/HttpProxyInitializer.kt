package io.qalipsis.plugins.netty.proxy.server.handlers

import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.qalipsis.plugins.netty.proxy.server.Handler
import io.qalipsis.plugins.netty.proxy.server.ProxyConfiguration
import io.qalipsis.plugins.netty.proxy.server.ProxyingContext
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * Initializer of a channel to proxy data with a HTTP proxy.
 *
 * @author Eric Jessé
 */
internal class HttpProxyInitializer(
    private val configuration: ProxyConfiguration,
    private val requestCounter: AtomicInteger
) : ChannelInitializer<Channel>() {

    override fun initChannel(ch: Channel) {
        val frontEndAddress = ch.remoteAddress() as InetSocketAddress
        val context =
            ProxyingContext(
                configuration,
                ProxyingContext.Address(frontEndAddress.hostName, frontEndAddress.port),
                ch,
                requestCounter
            )
        ch.pipeline().addLast(context.handler(Handler.HTTP1_FRONTEND)).remove(this)
    }
}
