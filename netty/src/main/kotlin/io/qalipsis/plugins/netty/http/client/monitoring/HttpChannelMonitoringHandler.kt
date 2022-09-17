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

package io.qalipsis.plugins.netty.http.client.monitoring

import io.netty.channel.ChannelHandlerContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.netty.handlers.monitoring.ChannelMonitoringHandler
import io.qalipsis.plugins.netty.monitoring.MonitoringCollector

/**
 * Channel handler to record the activity of an HTTP channel.
 *
 * @author Eric Jessé
 */
internal class HttpChannelMonitoringHandler(
    monitoringCollector: MonitoringCollector
) : ChannelMonitoringHandler(monitoringCollector) {

    /**
     * The completion should be recorded by the response handler.
     */
    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.fireChannelReadComplete()
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
