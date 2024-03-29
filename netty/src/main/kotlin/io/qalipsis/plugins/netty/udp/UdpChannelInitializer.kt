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

package io.qalipsis.plugins.netty.udp

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.DatagramChannel
import io.netty.handler.codec.DatagramPacketDecoder
import io.netty.handler.codec.bytes.ByteArrayDecoder
import io.netty.handler.codec.bytes.ByteArrayEncoder
import io.qalipsis.plugins.netty.PipelineHandlerNames

/**
 * Channel initializer for UDP clients.
 *
 * @author Eric Jessé
 */
internal class UdpChannelInitializer : ChannelInitializer<DatagramChannel>() {

    override fun initChannel(channel: DatagramChannel) {
        val pipeline = channel.pipeline()

        channel.pipeline().addLast(PipelineHandlerNames.REQUEST_DECODER, DatagramPacketDecoder(ByteArrayDecoder()))
        channel.pipeline().addLast(PipelineHandlerNames.REQUEST_ENCODER, ByteArrayEncoder())

        // The handler is no longer required once everything was initialized.
        pipeline.remove(this)
    }

}
