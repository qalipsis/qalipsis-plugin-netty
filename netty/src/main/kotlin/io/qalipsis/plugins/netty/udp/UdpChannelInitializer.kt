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
