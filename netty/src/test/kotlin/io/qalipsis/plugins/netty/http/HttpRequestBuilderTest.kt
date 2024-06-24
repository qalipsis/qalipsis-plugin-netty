/*
 * Copyright 2024 AERIS IT Solutions GmbH
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

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.spyk
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.qalipsis.api.context.StepContext
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.http.request.FormOrMultipartHttpRequest
import io.qalipsis.plugins.netty.http.request.HttpRequest
import io.qalipsis.plugins.netty.http.request.SimpleHttpRequest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.steps.StepTestHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class HttpRequestBuilderTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    fun `should contain the basic request headers for a simple HTTP request`() = testDispatcherProvider.runTest {
        //given
        val requestSpecification: suspend HttpRequestBuilder.(StepContext<*, *>, String) -> HttpRequest<*> =
            { _, _ -> SimpleHttpRequest(HttpMethod.GET, "/head")
                .addParameter("param1", "value1")
                .addParameter("param1", "value2")
                .addParameter("param2", "value3")
                .addHeader(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
                .withBasicAuth("foo", "bar") }

        val requestBuilder = HttpRequestBuilderImpl
        val ctx =
        spyk(StepTestHelper.createStepContext<String, RequestResult<String, ByteArray, *>>(input = "This is a basic test"))

        //when
        val specs = requestSpecification(requestBuilder, ctx, "Basic Test")

        //then
        assertThat(specs.uri).isEqualTo("/head")
        assertThat(specs.method).isEqualTo(HttpMethod.GET)
        assertThat(specs).all {
            transform { it.parameters["param1"] }.isEqualTo(listOf("value1", "value2"))
            transform { it.parameters["param2"] }.isEqualTo(listOf("value3"))
            transform { it.headers[HttpHeaderNames.AUTHORIZATION] }.isEqualTo("Basic Zm9vOmJhcg==")
            transform { it.headers[HttpHeaderNames.ACCEPT] }.isEqualTo(HttpHeaderValues.APPLICATION_JSON)
        }
    }

    @Test
    fun `should contain the basic request headers for a Multiform HTTP request`() = testDispatcherProvider.runTest {
        //given
        val requestSpecification: suspend HttpRequestBuilder.(StepContext<*, *>, String) -> HttpRequest<*> =
            { _, _ -> FormOrMultipartHttpRequest(HttpMethod.POST, "/head", true)
                .addParameter("param1", "value4")
                .addParameter("param1", "value5")
                .addParameter("param2", "value6")
                .addHeader(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
                .withBasicAuth("localhost", "password") }

        val requestBuilder = HttpRequestBuilderImpl
        val ctx =
            spyk(StepTestHelper.createStepContext<String, RequestResult<String, ByteArray, *>>(input = "This is a multiform test"))

        //when
        val specs = requestSpecification(requestBuilder, ctx, "Basic Test2")

        //then
        assertThat(specs.uri).isEqualTo("/head")
        assertThat(specs.method).isEqualTo(HttpMethod.POST)
        assertThat(specs).all {
            transform { it.parameters["param1"] }.isEqualTo(listOf("value4", "value5"))
            transform { it.parameters["param2"] }.isEqualTo(listOf("value6"))
            transform { it.headers[HttpHeaderNames.AUTHORIZATION] }.isEqualTo("Basic bG9jYWxob3N0OnBhc3N3b3Jk")
            transform { it.headers[HttpHeaderNames.ACCEPT] }.isEqualTo(HttpHeaderValues.APPLICATION_JSON)
        }
    }
}