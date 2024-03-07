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