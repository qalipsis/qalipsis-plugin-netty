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
