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
