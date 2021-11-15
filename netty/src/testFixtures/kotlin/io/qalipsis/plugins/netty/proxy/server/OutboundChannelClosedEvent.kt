package io.qalipsis.plugins.netty.proxy.server

internal data class OutboundChannelClosedEvent(val proxyingContext: ProxyingContext, val isClient: Boolean)
