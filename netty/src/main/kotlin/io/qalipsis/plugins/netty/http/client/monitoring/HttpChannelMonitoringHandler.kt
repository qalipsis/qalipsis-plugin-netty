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
