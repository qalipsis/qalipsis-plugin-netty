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

package io.qalipsis.plugins.netty.http.response

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSameSizeAs
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.key
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.invokeInvisible
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import io.netty.buffer.ByteBufAllocator
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.EmptyHttpHeaders
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.cookie.Cookie
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyNever
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@WithMockk
internal class ResponseConverterTest {

    @RelaxedMockK
    private lateinit var deserializer1: HttpBodyDeserializer

    @RelaxedMockK
    private lateinit var deserializer2: HttpBodyDeserializer

    @Test
    internal fun `should convert the response with the deserialized body`() {
        // given
        val bytes = "This is the content".toByteArray()
        val headers = DefaultHttpHeaders(false)
        // Mix cases in the headers names.
        headers["Set-CoOkie"] = listOf("yummy_cookie=choco", "tasty_cookie=strawberry")
        headers["content-length"] = 19
        headers["Content-Encoding"] = "gzip"
        headers["Content-type"] = MediaType.APPLICATION_JSON + ";charset=UTF-8"

        val nettyResponse = spyk(
            DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.ACCEPTED,
                ByteBufAllocator.DEFAULT.directBuffer(bytes.size).apply { writeBytes(bytes) },
                headers,
                EmptyHttpHeaders.INSTANCE
            )
        )

        val deserializedBody = Entity("value")
        val responseConverter = spyk(ResponseConverter<Entity>(Entity::class, emptyList()), recordPrivateCalls = true)
        val capturedBytes = slot<ByteArray>()
        every {
            responseConverter["convertBody"](
                refEq(nettyResponse),
                capture(capturedBytes),
                any<MediaType>()
            )
        } returns deserializedBody

        // when
        val response = responseConverter.convert(nettyResponse)

        // then
        assertThat(capturedBytes.captured).all {
            hasSameSizeAs(bytes)
            repeat(bytes.size) { index(it).isEqualTo(bytes[it]) }
        }
        assertThat(response).all {
            prop(HttpResponse<Entity>::status).isSameAs(HttpResponseStatus.ACCEPTED)
            prop(HttpResponse<Entity>::body).isSameAs(deserializedBody)
            prop(HttpResponse<Entity>::bodyBytes).isSameAs(capturedBytes.captured)
            prop(HttpResponse<Entity>::contentType).isEqualTo(MediaType("application", "json", Charsets.UTF_8))
            prop(HttpResponse<Entity>::cookies).all {
                hasSize(2)
                key("yummy_cookie").prop(Cookie::value).isEqualTo("choco")
                key("tasty_cookie").prop(Cookie::value).isEqualTo("strawberry")
            }
            prop(HttpResponse<Entity>::headers).all {
                hasSize(3)
                key("content-length").isEqualTo("19")
                key("content-encoding").isEqualTo("gzip")
                key("content-type").isEqualTo(MediaType.APPLICATION_JSON + ";charset=UTF-8")
            }
        }
        verify {
            nettyResponse.release()
            responseConverter["convertBody"](
                refEq(nettyResponse),
                refEq(capturedBytes.captured),
                eq(MediaType(MediaType.APPLICATION_JSON + ";charset=UTF-8"))
            )
        }
    }

    @Test
    internal fun `should convert the response with the deserialized body and the default content type`() {
        // given
        val bytes = "This is the content".toByteArray()
        val headers = DefaultHttpHeaders(false)
        // Mix cases in the headers names.
        headers["Set-CoOkie"] = listOf("yummy_cookie=choco", "tasty_cookie=strawberry")
        headers["content-length"] = 19
        headers["Content-Encoding"] = "gzip"

        val nettyResponse = spyk(
            DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.ACCEPTED,
                ByteBufAllocator.DEFAULT.directBuffer(bytes.size).apply { writeBytes(bytes) },
                headers,
                EmptyHttpHeaders.INSTANCE
            )
        )

        val deserializedBody = Entity("value")
        val responseConverter = spyk(ResponseConverter<Entity>(Entity::class, emptyList()), recordPrivateCalls = true)
        val capturedBytes = slot<ByteArray>()
        every {
            responseConverter["convertBody"](
                refEq(nettyResponse),
                capture(capturedBytes),
                any<MediaType>()
            )
        } returns deserializedBody

        // when
        val response = responseConverter.convert(nettyResponse)

        // then
        assertThat(capturedBytes.captured).all {
            hasSameSizeAs(bytes)
            repeat(bytes.size) { index(it).isEqualTo(bytes[it]) }
        }
        assertThat(response).all {
            prop(HttpResponse<Entity>::status).isSameAs(HttpResponseStatus.ACCEPTED)
            prop(HttpResponse<Entity>::body).isSameAs(deserializedBody)
            prop(HttpResponse<Entity>::bodyBytes).isSameAs(capturedBytes.captured)
            prop(HttpResponse<Entity>::contentType).isNull()
            prop(HttpResponse<Entity>::cookies).all {
                hasSize(2)
                key("yummy_cookie").prop(Cookie::value).isEqualTo("choco")
                key("tasty_cookie").prop(Cookie::value).isEqualTo("strawberry")
            }
            prop(HttpResponse<Entity>::headers).all {
                hasSize(2)
                key("content-length").isEqualTo("19")
                key("content-encoding").isEqualTo("gzip")
            }
        }
        verify {
            nettyResponse.release()
            responseConverter["convertBody"](
                refEq(nettyResponse),
                refEq(capturedBytes.captured),
                refEq(MediaType.TEXT_PLAIN_TYPE)
            )
        }
    }

    @Test
    internal fun `should convert a non full netty response`() {
        // given
        val headers = DefaultHttpHeaders(false)
        // Mix cases in the headers names.
        headers["Set-CoOkie"] = listOf("yummy_cookie=choco", "tasty_cookie=strawberry")
        headers["content-length"] = 19
        headers["Content-Encoding"] = "gzip"

        val nettyResponse = relaxedMockk<io.netty.handler.codec.http.HttpResponse> {
            every { status() } returns HttpResponseStatus.ACCEPTED
        }
        val responseConverter = spyk(ResponseConverter<Entity>(Entity::class, emptyList()), recordPrivateCalls = true)

        // when
        val response = responseConverter.convert(nettyResponse)

        // then
        verifyNever {
            responseConverter["convertBody"](
                any<FullHttpResponse>(),
                any<ByteArray>(),
                any<MediaType>()
            )
        }
        assertThat(response).all {
            prop(HttpResponse<Entity>::status).isSameAs(HttpResponseStatus.ACCEPTED)
            prop(HttpResponse<Entity>::body).isNull()
            prop(HttpResponse<Entity>::bodyBytes).isNull()
            prop(HttpResponse<Entity>::contentType).isNull()
            prop(HttpResponse<Entity>::cookies).isEmpty()
            prop(HttpResponse<Entity>::headers).isEmpty()
        }
    }

    @Test
    internal fun `should convert the body to Unit`() {
        // given
        val bytes = "This is the content ÉÖß".toByteArray()
        val mediaType = MediaType.APPLICATION_JSON_TYPE
        val nettyResponse = relaxedMockk<FullHttpResponse> {
            every { status() } returns HttpResponseStatus.ACCEPTED
        }
        val responseConverter = ResponseConverter<Unit>(Unit::class, listOf(deserializer1, deserializer2))

        // when
        val body: Unit = responseConverter.invokeInvisible("convertBody", nettyResponse, bytes, mediaType)

        // then
        assertThat(body).isEqualTo(Unit)
        confirmVerified(deserializer1, deserializer2)
    }

    @Test
    internal fun `should convert the body to a string with the provided encoding`() {
        // given
        val bytes = "This is the content ÉÖß".toByteArray(Charsets.UTF_32BE)
        val mediaType = MediaType("any/any; charset=${Charsets.UTF_32BE}")
        val nettyResponse = relaxedMockk<FullHttpResponse> {
            every { status() } returns HttpResponseStatus.ACCEPTED
        }
        val responseConverter = ResponseConverter<String>(String::class, listOf(deserializer1, deserializer2))

        // when
        val body: String = responseConverter.invokeInvisible("convertBody", nettyResponse, bytes, mediaType)

        // then
        assertThat(body).isEqualTo("This is the content ÉÖß")
        confirmVerified(deserializer1, deserializer2)
    }

    @Test
    internal fun `should return the byte array as body`() {
        // given
        val bytes = "This is the content ÉÖß".toByteArray(Charsets.UTF_32BE)
        val mediaType = MediaType("any/any; charset=${Charsets.UTF_32BE}")
        val nettyResponse = relaxedMockk<FullHttpResponse> {
            every { status() } returns HttpResponseStatus.ACCEPTED
        }
        val responseConverter = ResponseConverter<ByteArray>(ByteArray::class, listOf(deserializer1, deserializer2))

        // when
        val body: ByteArray = responseConverter.invokeInvisible("convertBody", nettyResponse, bytes, mediaType)

        // then
        assertThat(body).isSameAs(bytes)
        confirmVerified(deserializer1, deserializer2)
    }

    @Test
    internal fun `should deserialize as a null body when request is not a success`() {
        // given
        val bytes = "This is the content ÉÖß".toByteArray(Charsets.UTF_32BE)
        val mediaType = MediaType("any/any; charset=${Charsets.UTF_32BE}")
        val nettyResponse = relaxedMockk<FullHttpResponse> {
            every { status() } returns HttpResponseStatus.INTERNAL_SERVER_ERROR
        }
        val responseConverter = ResponseConverter<ByteArray>(ByteArray::class, listOf(deserializer1, deserializer2))

        // when
        val body: ByteArray? = responseConverter.invokeInvisible("convertBody", nettyResponse, bytes, mediaType)

        // then
        assertThat(body).isNull()
        confirmVerified(deserializer1, deserializer2)
    }

    @Test
    internal fun `should deserialize only with the matching deserializer`() {
        // given
        val bytes = "This is the content ÉÖß".toByteArray(Charsets.UTF_32BE)
        val mediaType = MediaType.APPLICATION_JSON_TYPE
        val nettyResponse = relaxedMockk<FullHttpResponse> {
            every { status() } returns HttpResponseStatus.ACCEPTED
        }
        val result = Entity("value")
        every { deserializer1.accept(refEq(mediaType)) } returns false
        every { deserializer2.accept(refEq(mediaType)) } returns true
        every { deserializer2.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class)) } returns result

        val responseConverter = ResponseConverter<Entity>(Entity::class, listOf(deserializer1, deserializer2))

        // when
        val body: Entity? = responseConverter.invokeInvisible("convertBody", nettyResponse, bytes, mediaType)

        // then
        assertThat(body).isSameAs(result)
        verifyOrder {
            deserializer1.accept(refEq(mediaType))
            deserializer2.accept(refEq(mediaType))
            deserializer2.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class))
        }
        confirmVerified(deserializer1, deserializer2)
    }

    @Test
    internal fun `should deserialize with the first working matching deserializer`() {
        // given
        val bytes = "This is the content ÉÖß".toByteArray(Charsets.UTF_32BE)
        val mediaType = MediaType.APPLICATION_JSON_TYPE
        val nettyResponse = relaxedMockk<FullHttpResponse> {
            every { status() } returns HttpResponseStatus.ACCEPTED
        }
        val result = Entity("value")
        every { deserializer1.accept(refEq(mediaType)) } returns true
        every { deserializer1.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class)) } throws RuntimeException()
        every { deserializer2.accept(refEq(mediaType)) } returns true
        every { deserializer2.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class)) } returns result

        val responseConverter = ResponseConverter<Entity>(Entity::class, listOf(deserializer1, deserializer2))

        // when
        val body: Entity? = responseConverter.invokeInvisible("convertBody", nettyResponse, bytes, mediaType)

        // then
        assertThat(body).isSameAs(result)
        verifyOrder {
            deserializer1.accept(refEq(mediaType))
            deserializer1.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class))
            deserializer2.accept(refEq(mediaType))
            deserializer2.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class))
        }
        confirmVerified(deserializer1, deserializer2)
    }

    @Test
    internal fun `should deserialize as a null body when no serializer succeeds`() {
        // given
        val bytes = "This is the content ÉÖß".toByteArray(Charsets.UTF_32BE)
        val mediaType = MediaType.APPLICATION_JSON_TYPE
        val nettyResponse = relaxedMockk<FullHttpResponse> {
            every { status() } returns HttpResponseStatus.ACCEPTED
        }
        every { deserializer1.accept(refEq(mediaType)) } returns true
        every { deserializer1.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class)) } throws RuntimeException()
        every { deserializer2.accept(refEq(mediaType)) } returns true
        every { deserializer2.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class)) } throws RuntimeException()

        val responseConverter = ResponseConverter<Entity>(Entity::class, listOf(deserializer1, deserializer2))

        // when
        val body: Entity? = responseConverter.invokeInvisible("convertBody", nettyResponse, bytes, mediaType)

        // then
        assertThat(body).isNull()
        verifyOrder {
            deserializer1.accept(refEq(mediaType))
            deserializer1.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class))
            deserializer2.accept(refEq(mediaType))
            deserializer2.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class))
        }
        confirmVerified(deserializer1, deserializer2)
    }

    private data class Entity(val field: String)
}
