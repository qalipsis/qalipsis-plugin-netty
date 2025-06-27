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

package io.qalipsis.plugins.netty.http.server

import io.aerisconsulting.catadioptre.getProperty
import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.FilterChain
import io.micronaut.http.filter.HttpFilter
import io.micronaut.http.netty.stream.StreamedHttpRequest
import io.micronaut.http.server.netty.NettyHttpRequest
import io.micronaut.http.server.netty.NettyHttpServer
import io.micronaut.runtime.Micronaut
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.EventLoopGroup
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.codec.http.multipart.Attribute
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory
import io.netty.handler.codec.http.multipart.FileUpload
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.netty.Server
import org.junit.jupiter.api.extension.ExtensionContext
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Embedded HTTP Server for test purpose. Support HTTPS with self-signed certificate.
 *
 * @author Eric Jessé
 */
class HttpServer private constructor(
    private val args: List<String>,
    val secured: Boolean
) : Server {

    private var applicationContext: ApplicationContext? = null

    private lateinit var embeddedServer: EmbeddedServer

    private val scheme = if (secured) "https" else "http"

    private val requestCounter = AtomicInteger()

    private val firstRequestTimestampHolder = AtomicLong()

    private val lastRequestTimestampHolder = AtomicLong()

    /**
     * Provides the number of requests received during the test.
     */
    val requestCount: Int
        get() = requestCounter.get()

    /**
     * Provides the timestamp of the earliest received request, in ms since Epoch.
     */
    val firstRequestTimestamp: Long
        get() = firstRequestTimestampHolder.get()

    /**
     * Provides the timestamp of the latest received request, in ms since Epoch.
     */
    val lastRequestTimestamp: Long
        get() = lastRequestTimestampHolder.get()

    override val port: Int
        get() = embeddedServer.port

    /**
     * Returns the local URL to use to access the server.
     */
    val url: String
        get() = "$scheme://localhost:$port"

    override fun start() {
        applicationContext = Micronaut
            .build(*args.toTypedArray())
            .classes(WebFilter::class.java)
            .start()
        embeddedServer = applicationContext!!.getBean(EmbeddedServer::class.java)
        applicationContext!!.getBean(WebFilter::class.java).server = this
    }

    override fun stop() {
        embeddedServer.stop()
        applicationContext?.stop()
    }

    override fun beforeEach(context: ExtensionContext) {
        requestCounter.set(0)
        firstRequestTimestampHolder.set(0)
        lastRequestTimestampHolder.set(0)
    }

    /**
     * Forces the server to shutdown without grace delay.
     */
    fun forceKill() {
        (embeddedServer as NettyHttpServer).apply {
            kotlin.runCatching {
                getProperty<EventLoopGroup>("workerGroup").shutdownGracefully(0, 0, TimeUnit.MILLISECONDS)
            }
            kotlin.runCatching {
                getProperty<EventLoopGroup>("parentGroup").shutdownGracefully(0, 0, TimeUnit.MILLISECONDS)
            }
        }
        embeddedServer.stop()
        applicationContext?.stop()
    }

    @Filter(Filter.MATCH_ALL_PATTERN)
    open internal class WebFilter(
        private val conversionService: ConversionService<*>
    ) : HttpFilter {

        lateinit var server: HttpServer

        private val decoderFactory = DefaultHttpDataFactory(DefaultHttpDataFactory.MAXSIZE)

        override fun doFilter(request: HttpRequest<*>, chain: FilterChain): Publisher<out HttpResponse<*>> {
            val now = System.currentTimeMillis()
            server.firstRequestTimestampHolder.compareAndSet(0, now)
            server.lastRequestTimestampHolder.set(now)
            server.requestCounter.incrementAndGet()
            return when (request.uri.path.substringAfter("/").substringBefore("/")) {
                "kill" -> {
                    log.info { "Killing the server" }
                    server.forceKill()
                    throw RuntimeException("Server was killed")
                }
                "delay" -> {
                    Mono.just(HttpResponse.noContent<Unit>()).delaySubscription(resolveTimeout(request))
                }
                "hello" -> {
                    Mono.just(HttpResponse.ok("Hello, world!"))
                }
                "status" -> {
                    Mono.just(HttpResponse.status<String>(resolveStatus(request)).body("Hello, world!"))
                }
                "redirect" -> {
                    Mono.just(HttpResponse.redirect<Unit>(URI(request.parameters["location"] ?: "/")))
                }
                "permanent-redirect" -> {
                    Mono.just(HttpResponse.permanentRedirect<Unit>(URI(request.parameters["location"] ?: "/")))
                }
                "temporary-redirect" -> {
                    Mono.just(HttpResponse.temporaryRedirect<Unit>(URI(request.parameters["location"] ?: "/")))
                }
                else -> describeRequestAsJson(request).map { HttpResponse.ok(it) }
            }
        }

        private fun resolveTimeout(request: HttpRequest<*>): Duration {
            return request.parameters["timeout"]
                ?.let { conversionService.convertRequired(it, Duration::class.java) }
                ?: Duration.ofMinutes(1)
        }

        private fun resolveStatus(request: HttpRequest<*>): HttpStatus {
            return request.parameters["status"]
                ?.let { conversionService.convertRequired(it, HttpStatus::class.java) }
                ?: HttpStatus.OK
        }

        /**
         * Fully describes the [HttpRequest] as JSON.
         */
        private fun describeRequestAsJson(request: HttpRequest<*>): Flux<String> {
            val uri = request.uri
            val requestMetadata = RequestMetadata(
                uri.toString(), uri.path, request.httpVersion.name,
                request.methodName
            )
            requestMetadata.parameters.putAll(QueryStringDecoder(uri).parameters())
            requestMetadata.headers.putAll(request.headers.filterNot { it.key == HttpHeaderNames.COOKIE.toString() }
                .map { it.key to it.value.first() })
            requestMetadata.cookies.putAll(request.cookies.all.map { cookie -> cookie.name to cookie.value })

            val result = getNettyNativeContent(request as NettyHttpRequest<*>)
                .collectList()
                .map { httpContents ->
                    describeContent(request.nativeRequest, httpContents, requestMetadata)
                    requestMetadata
                }.defaultIfEmpty(requestMetadata)
                .doOnNext {
                    log.info { "Returning response $it" }
                }
                .map(RequestMetadata::toJson)

            return Flux.from(result)
        }

        /**
         * Extracts the received [HttpContent], whether it is streamed or not.
         */
        private fun getNettyNativeContent(nettyRequest: NettyHttpRequest<*>): Flux<HttpContent> {
            val nativeRequest = nettyRequest.nativeRequest
            return if (nativeRequest is StreamedHttpRequest) {
                Flux.from(nativeRequest)
            } else {
                val httpContent = kotlin.runCatching { nativeRequest.getProperty<HttpContent>("request") }
                    .getOrDefault(nativeRequest) as? HttpContent
                Flux.just(httpContent)
            }
        }

        /**
         * Describes the received [HttpContent] into the [RequestMetadata].
         */
        private fun describeContent(
            httpRequest: io.netty.handler.codec.http.HttpRequest,
            httpContents: List<HttpContent>,
            requestMetadata: RequestMetadata
        ) {
            log.trace { "Describing the content of the HTTP request" }
            requestMetadata.size = httpContents.sumBy { it.content().readableBytes() }
            var describedContent = false
            var decoder: HttpPostRequestDecoder? = null
            try {
                decoder = HttpPostRequestDecoder(decoderFactory, httpRequest)
                requestMetadata.multipart = decoder.isMultipart

                httpContents.forEach(decoder::offer)
                if (decoder.hasNext()) {
                    decoder.bodyHttpDatas.forEach { data ->
                        if (data is FileUpload) {
                            requestMetadata.files.computeIfAbsent(data.name) { mutableListOf() }
                                .add(
                                    FileMetadata(
                                        data.filename,
                                        data.contentType,
                                        data.definedLength().toInt()
                                    )
                                )
                        } else if (data is Attribute) {
                            requestMetadata.form.computeIfAbsent(data.name) { mutableListOf() }
                                .add(data.value)
                        }
                    }
                    describedContent = true
                }
            } catch (e: Exception) {
                // Ignore.
            } finally {
                decoder?.let {
                    decoder.cleanFiles()
                    decoder.destroy()
                }
            }

            if (!describedContent) {
                requestMetadata.data =
                    httpContents.first().content().apply { resetReaderIndex() }.toString(StandardCharsets.UTF_8)
            }

            httpContents.forEach {
                kotlin.runCatching { it.release() }
            }
        }
    }

    companion object {

        @JvmStatic
        fun new(
            version: io.qalipsis.plugins.netty.http.spec.HttpVersion = io.qalipsis.plugins.netty.http.spec.HttpVersion.HTTP_1_1,
            port: Int? = null,
            enableTls: Boolean = false
        ): HttpServer {
            val args = mutableListOf<String>()
            args += "micronaut.server.netty.log-level=trace"
            args += "micronaut.server.netty.validate-headers=false"

            if (version == io.qalipsis.plugins.netty.http.spec.HttpVersion.HTTP_2_0) {
                args += "--micronaut.server.http-version=2.0"
            }
            val actualPort = port ?: "\${random.port}" //ServerUtils.availableTcpPort()
            if (enableTls) {
                args += "--micronaut.ssl.enabled=true"
                args += "--micronaut.ssl.buildSelfSigned=true"
                args += "--micronaut.ssl.port=$actualPort"
            } else {
                args += "--micronaut.server.port=$actualPort"
            }
            return HttpServer(args, enableTls)
        }

        @JvmStatic
        private val log = logger()
    }
}
