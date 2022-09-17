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
