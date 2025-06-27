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

package io.qalipsis.plugins.netty.http.client

import io.netty.channel.EventLoopGroup
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.util.ReferenceCounted
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.Slot
import io.qalipsis.plugins.netty.http.request.HttpRequest
import io.qalipsis.plugins.netty.http.request.InternalHttpRequest
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.SocketClient
import io.qalipsis.plugins.netty.socket.SocketMonitoringCollector
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * HTTP client able to maintain several [HttpClient] internally. That kind of step is required to support clients
 * attached to a single user, where there are redirections requiring different connections (for example from
 * HTTP to HTTPS, or when a server was relocated).
 *
 * @author Eric Jessé
 */
internal class MultiSocketHttpClient(
    plannedUsages: Long = 1,
    private val onClose: MultiSocketHttpClient.() -> Unit = {}
) : SocketClient<HttpClientConfiguration, HttpRequest<*>, HttpResponse, MultiSocketHttpClient>(plannedUsages) {

    private var open = false

    private lateinit var clientConfiguration: HttpClientConfiguration

    /**
     * Map of all clients attached to the different remote peers.
     */
    private val clients = ConcurrentHashMap<RemotePeerIdentifier, Slot<HttpClient>>()

    /**
     * Map of all clients being currently used.
     */
    private val borrowedClients = ConcurrentHashMap<RemotePeerIdentifier, HttpClient>()

    /**
     * Mutexes attached to each remote host.
     */
    private val peerMutexes = ConcurrentHashMap<RemotePeerIdentifier, Mutex>()

    private lateinit var workerGroup: EventLoopGroup

    override val isOpen: Boolean
        get() = borrowedClients.values.all { it.isOpen } && clients.values.all { it.getOrNull()?.isOpen != false }

    override suspend fun open(
        clientConfiguration: HttpClientConfiguration,
        workerGroup: EventLoopGroup,
        monitoringCollector: SocketMonitoringCollector
    ) {
        log.trace { "Opening the HTTP client" }
        this.workerGroup = workerGroup
        this.clientConfiguration = clientConfiguration
        createNewClient(clientConfiguration, workerGroup, monitoringCollector)
        log.trace { "HTTP client is now open" }
        open = true
    }

    override suspend fun close() {
        log.trace { "Closing the HTTP client" }
        open = false
        log.trace { "Closing enclosed ${clients.size} clients" }
        clients.values.forEach {
            tryAndLogOrNull(log) { it.get().close() }
        }
        borrowedClients.clear()
        clients.clear()
        log.trace { "HTTP client is now closed" }
        try {
            this.onClose()
        } catch (e: Exception) {
            log.trace { "Close hook on the HTTP client threw an exception: ${e.message}" }
            log.info(e) { e.message }
        }
    }

    private suspend fun createNewClient(
        clientConfiguration: HttpClientConfiguration,
        workerGroup: EventLoopGroup,
        monitoringCollector: SocketMonitoringCollector
    ): Slot<HttpClient> {
        val client = HttpClient(Long.MAX_VALUE) {
            clients.remove(this.peerIdentifier)
            peerMutexes.remove(this.peerIdentifier)
        }
        client.open(clientConfiguration, workerGroup, monitoringCollector)
        return Slot(client).apply {
            clients[client.peerIdentifier] = this
            peerMutexes[client.peerIdentifier] = Mutex(false)
        }
    }

    override suspend fun <I> execute(
        stepContext: StepContext<I, *>,
        request: HttpRequest<*>,
        monitoringCollector: StepContextBasedSocketMonitoringCollector
    ): HttpResponse {
        var response = doExecute(monitoringCollector, stepContext, request)
        var redirections = 0
        while (clientConfiguration.followRedirections && (++redirections) <= clientConfiguration.maxRedirections
            && response.status() in REDIRECTION_STATUS
        ) {
            val location = response.headers()[HttpHeaderNames.LOCATION]
            (response as? ReferenceCounted)?.release()
            val forwardedRequest = if (response.status() == HttpResponseStatus.SEE_OTHER) {
                (request as InternalHttpRequest<*, *>).with(location, HttpMethod.GET)
            } else {
                (request as InternalHttpRequest<*, *>).with(location)
            }
            response = doExecute(monitoringCollector, stepContext, forwardedRequest)
        }

        return response
    }

    private suspend fun doExecute(
        monitoringCollector: StepContextBasedSocketMonitoringCollector,
        context: StepContext<*, *>,
        request: HttpRequest<*>
    ): HttpResponse {
        val requestUri = (request as InternalHttpRequest<*, *>).computeUri(clientConfiguration)
        val requestPeerIdentifier = RemotePeerIdentifier.of(requestUri)

        // Synchronizes the search of a client for a given remote destination.
        val client = peerMutexes.computeIfAbsent(requestPeerIdentifier) {
            Mutex(false)
        }.withLock {
            clients[requestPeerIdentifier] ?: createNewClient(
                clientConfiguration.copy().apply { url(requestUri) },
                workerGroup,
                monitoringCollector
            )
        }.remove()

        return if (client.isOpen) {
            try {
                borrowedClients[requestPeerIdentifier] = client
                client.execute(context, request, monitoringCollector)
            } finally {
                clients[requestPeerIdentifier]!!.set(client)
                borrowedClients.remove(requestPeerIdentifier)
            }
        } else {
            // If the client is not open, we close it properly and recreate a new one.
            log.trace { "The client for the remote peer $requestPeerIdentifier is not open, creating a new one" }
            clients.remove(requestPeerIdentifier)
            client.close()
            doExecute(monitoringCollector, context, request)
        }
    }

    override fun toString(): String {
        return "MultiSocketHttpClient(open=$open)"
    }


    companion object {

        @JvmStatic
        private val REDIRECTION_STATUS = setOf(
            HttpResponseStatus.MOVED_PERMANENTLY,
            HttpResponseStatus.TEMPORARY_REDIRECT,
            HttpResponseStatus.PERMANENT_REDIRECT,
            HttpResponseStatus.SEE_OTHER
        )

        @JvmStatic
        private val log = logger()
    }
}
