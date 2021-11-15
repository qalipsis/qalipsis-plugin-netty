package io.qalipsis.plugins.netty

/**
 * Names of the handler in the different Netty [io.netty.channel.ChannelPipeline]s,
 *
 * @author Eric Jessé
 */
object PipelineHandlerNames {

    const val PROXY_HANDLER = "proxy.handler"
    const val REQUEST_DECODER = "request.decoder"
    const val REQUEST_ENCODER = "request.encoder"
    const val CLIENT_CODEC = "client.codec"
    const val CONNECTION_HANDLER = "connection.handler"
    const val REQUEST_HANDLER = "request.handler"
    const val RESPONSE_DECOMPRESSOR = "response.decompressor"
    const val REQUEST_HEARTBEAT_HANDLER = "request.heartBeatHandler"

}
