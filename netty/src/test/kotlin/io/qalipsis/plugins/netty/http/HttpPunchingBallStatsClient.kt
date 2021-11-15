package io.qalipsis.plugins.netty.http

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.fasterxml.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Client to work with stats of the Docker image aerisconsulting/http-punching-ball.
 */
class HttpPunchingBallStatsClient(scheme: String = "http", host: String = "localhost", port: Int) {

    private val uri = URI.create("$scheme://$host:$port/stats")

    fun get(): RequestsStats {
        return JSON_MAPPER.readValue(uri.toURL())
    }

    fun reset() {
        val statsRequests = HttpRequest.newBuilder().DELETE().uri(uri).build()
        HttpClient.newBuilder().build().send(statsRequests, HttpResponse.BodyHandlers.ofString())
    }

    private companion object {

        @JvmStatic
        val JSON_MAPPER: ObjectMapper = jacksonMapperBuilder().also {
            it.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            it.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
        }.build()
    }
}

data class RequestsStats(val count: Long, val earliestEpochMs: Long, val latestEpochMs: Long)