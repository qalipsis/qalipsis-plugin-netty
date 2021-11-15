package io.qalipsis.plugins.netty.http

object HttpPipelineNames {

    const val CHANNEL_MONITORING_HANDLER = "http.channel-monitoring"

    const val INBOUND_HANDLER = "http.inbound-handler"

    const val HTTP2_SETTINGS_HANDLER = "http2.settings-handler"

    const val HTTP2_UPGRADE_CODEC = "http2.upgrade-code"

    const val AGGREGATOR_HANDLER = "http.aggregator-handler"

    const val CHUNKED_REQUEST_HANDLER = "http.chunk-request-handler"
}
