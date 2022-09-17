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
