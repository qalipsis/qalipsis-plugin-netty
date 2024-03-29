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
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.prop
import org.junit.jupiter.api.Test

internal class MediaTypeTest {

    @Test
    internal fun `should create a wildcard media type`() {
        val mediaType = MediaType("")

        assertThat(mediaType).all {
            prop(MediaType::type).equals("*")
            prop(MediaType::subtype).equals("*")
            prop(MediaType::charset).equals(Charsets.ISO_8859_1)
            prop(MediaType::extension).equals(null)
        }
    }

    @Test
    internal fun `should create a media type with default charset`() {
        val mediaType = MediaType("application/json")

        assertThat(mediaType).all {
            prop(MediaType::type).equals("application")
            prop(MediaType::subtype).equals("json")
            prop(MediaType::charset).equals(Charsets.ISO_8859_1)
            prop(MediaType::extension).equals(null)
        }
    }

    @Test
    internal fun `should create a media type with the specified charset`() {
        val mediaType = MediaType("text/html; charset=UTF-8")

        assertThat(mediaType).all {
            prop(MediaType::type).equals("text")
            prop(MediaType::subtype).equals("html")
            prop(MediaType::charset).equals(Charsets.UTF_8)
            prop(MediaType::extension).equals(null)
        }
    }

    @Test
    internal fun `should create a media type with the specified charset without space`() {
        val mediaType = MediaType("text/html;charset=UTF-8")

        assertThat(mediaType).all {
            prop(MediaType::type).equals("text")
            prop(MediaType::subtype).equals("html")
            prop(MediaType::charset).equals(Charsets.UTF_8)
            prop(MediaType::extension).equals(null)
        }
    }

    @Test
    internal fun `should create a media type with the specified lowercase charset`() {
        val mediaType = MediaType("text/html; charset=utf-8")

        assertThat(mediaType).all {
            prop(MediaType::type).equals("text")
            prop(MediaType::subtype).equals("html")
            prop(MediaType::charset).equals(Charsets.UTF_8)
            prop(MediaType::extension).equals(null)
        }
    }

    @Test
    internal fun `should match a media type having the same type and any subtype`() {
        val mediaType = MediaType("text/html; charset=utf-8")
        val wildCardMediaType = MediaType("text/*")

        assertThat(wildCardMediaType.matches(mediaType)).isTrue()
        assertThat(mediaType.matches(wildCardMediaType)).isFalse()
    }

    @Test
    internal fun `should not match a media type having a different type`() {
        val mediaType = MediaType("any/html; charset=utf-8")
        val wildCardMediaType = MediaType("text/*")

        assertThat(wildCardMediaType.matches(mediaType)).isFalse()
        assertThat(mediaType.matches(wildCardMediaType)).isFalse()
    }

    @Test
    internal fun `should match a media type having any type`() {
        val mediaType = MediaType("text/html; charset=utf-8")
        val wildCardMediaType = MediaType("*")

        assertThat(wildCardMediaType.matches(mediaType)).isTrue()
        assertThat(mediaType.matches(wildCardMediaType)).isFalse()
    }

    @Test
    internal fun `should match the exact same media type`() {
        val mediaType = MediaType("text/html; charset=utf-8")
        val otherCardMediaType = MediaType("text/html")

        assertThat(otherCardMediaType.matches(mediaType)).isTrue()
        assertThat(mediaType.matches(otherCardMediaType)).isTrue()
    }

    @Test
    internal fun `should not match a media type having a different subtype`() {
        val mediaType = MediaType("text/html; charset=utf-8")
        val wildCardMediaType = MediaType("text/plain")

        assertThat(wildCardMediaType.matches(mediaType)).isFalse()
        assertThat(mediaType.matches(wildCardMediaType)).isFalse()
    }
}
