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
