package io.qalipsis.plugins.netty.http

import io.netty.handler.codec.http.HttpResponse
import io.qalipsis.plugins.netty.http.request.HttpRequest
import io.qalipsis.plugins.netty.socket.SocketClientStep

/**
 * General interface for the HTTP steps.
 *
 * @author Eric Jessé
 */
internal interface HttpClientStep<I, R> : SocketClientStep<I, HttpRequest<*>, HttpResponse, R>
