package io.qalipsis.plugins.netty.socket

import io.netty.channel.Channel

/**
 * General interface for objects in charge of writing HTTP requests onto the wire.
 *
 * @author Eric Jessé
 */
interface RequestWriter {

    fun write(channel: Channel)

}
