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

package io.qalipsis.plugins.netty.http.http1

import io.netty.handler.codec.http.FullHttpMessage
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpUtil

/**
 *
 * Implementations of [HttpObjectAggregator] that forces the aggregation to finish when the last message is identified.
 *
 * @author Eric Jessé
 */
internal class Http1ComposableObjectAggregator(maxContentLength: Int) : HttpObjectAggregator(maxContentLength) {

    override fun finishAggregation(aggregated: FullHttpMessage) {
        // Closes the aggregation on the last message.
        if (!HttpUtil.isContentLengthSet(aggregated) && aggregated.content().readableBytes() > 0) {
            super.finishAggregation(aggregated)
        }
    }
}
