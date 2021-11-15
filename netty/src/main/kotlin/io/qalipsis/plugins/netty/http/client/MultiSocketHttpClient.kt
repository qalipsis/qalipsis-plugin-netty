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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * HTTP client able to maintain several [HttpClient] internally. That kind of step is required to support clients
 * attached to a single user, where there are redirections requiring different connections (for example from
 * HTTP to HTTPS, or when a server was relocated).
 *
 * @author Eric Jessé
 */
internal class MultiSocketHttpClient(
    plannedUsages: Long = 1,
    private val ioCoroutineScope: CoroutineScope,
    private val ioCoroutineContext: CoroutineContext,
    private val onClose: MultiSocketHttpClient.() -> Unit = {}
) : SocketClient<HttpClientConfiguration, HttpRequest<*>, HttpResponse, MultiSocketHttpClient>(
    plannedUsages, ioCoroutineContext
) {

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
        open = false
        clients.values.forEach {
            tryAndLogOrNull(log) { it.get().close() }
        }
        clients.clear()
        try {
            this.onClose()
        } catch (e: Exception) {
            log.warn(e) { e.message }
        }
    }

    private suspend fun createNewClient(
        clientConfiguration: HttpClientConfiguration,
        workerGroup: EventLoopGroup,
        monitoringCollector: SocketMonitoringCollector
    ): Slot<HttpClient> {
        val client = HttpClient(Long.MAX_VALUE, ioCoroutineScope, ioCoroutineContext) {
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

    private tailrec suspend fun doExecute(
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
            client.close()
            doExecute(monitoringCollector, context, request)
        }
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
