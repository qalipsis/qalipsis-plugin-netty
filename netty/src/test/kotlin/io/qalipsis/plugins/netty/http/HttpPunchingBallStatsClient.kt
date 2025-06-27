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

    private val uri = URI.create("$scheme://$host:$port/_stats")

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

data class RequestsStats(val requestsCount: Long, val earliestEpochMs: Long, val latestEpochMs: Long)