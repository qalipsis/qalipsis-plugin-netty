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

package io.qalipsis.plugins.netty.http

object HttpPipelineNames {

    const val CHANNEL_MONITORING_HANDLER = "http.channel-monitoring"

    const val INBOUND_HANDLER = "http.inbound-handler"

    const val HTTP2_SETTINGS_HANDLER = "http2.settings-handler"

    const val HTTP2_UPGRADE_CODEC = "http2.upgrade-code"

    const val AGGREGATOR_HANDLER = "http.aggregator-handler"

    const val CHUNKED_REQUEST_HANDLER = "http.chunk-request-handler"
}
