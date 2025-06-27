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
