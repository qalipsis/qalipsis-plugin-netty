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

package io.qalipsis.plugins.netty.http.spec

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.prop
import org.junit.jupiter.api.Test
import java.net.InetAddress

internal class HttpClientConfigurationTest {

    private val hostname = InetAddress.getLocalHost().hostName

    @Test
    internal fun `should configure the URL with default HTTP port`() {
        // given
        val config = HttpClientConfiguration()

        // when
        config.url("http://$hostname")

        // then
        assertThat(config).all {
            prop(HttpClientConfiguration::scheme).isEqualTo("http")
            prop(HttpClientConfiguration::host).isEqualTo(hostname)
            prop(HttpClientConfiguration::port).isEqualTo(80)
            prop(HttpClientConfiguration::contextPath).isEqualTo("")
            prop(HttpClientConfiguration::isSecure).isFalse()
        }
    }

    @Test
    internal fun `should configure the URL with default HTTPS port`() {
        // given
        val config = HttpClientConfiguration()

        // when
        config.url("https://$hostname")

        // then
        assertThat(config).all {
            prop(HttpClientConfiguration::scheme).isEqualTo("https")
            prop(HttpClientConfiguration::host).isEqualTo(hostname)
            prop(HttpClientConfiguration::port).isEqualTo(443)
            prop(HttpClientConfiguration::contextPath).isEqualTo("")
            prop(HttpClientConfiguration::isSecure).isTrue()
        }
    }

    @Test
    internal fun `should configure the URL with context path`() {
        // given
        val config = HttpClientConfiguration()

        // when
        config.url("https://$hostname:8080/distant/")

        // then
        assertThat(config).all {
            prop(HttpClientConfiguration::scheme).isEqualTo("https")
            prop(HttpClientConfiguration::host).isEqualTo(hostname)
            prop(HttpClientConfiguration::port).isEqualTo(8080)
            prop(HttpClientConfiguration::contextPath).isEqualTo("/distant")
            prop(HttpClientConfiguration::isSecure).isTrue()
        }
    }

    @Test
    internal fun `should configure the URL with context path and parameters`() {
        // given
        val config = HttpClientConfiguration()

        // when
        config.url("https://$hostname:8080/distant?foo=bar")

        // then
        assertThat(config).all {
            prop(HttpClientConfiguration::scheme).isEqualTo("https")
            prop(HttpClientConfiguration::host).isEqualTo(hostname)
            prop(HttpClientConfiguration::port).isEqualTo(8080)
            prop(HttpClientConfiguration::contextPath).isEqualTo("/distant")
            prop(HttpClientConfiguration::isSecure).isTrue()
        }
    }

    @Test
    internal fun `should configure the URL without context path`() {
        // given
        val config = HttpClientConfiguration()

        // when
        config.url("http://$hostname:8080")

        // then
        assertThat(config).all {
            prop(HttpClientConfiguration::scheme).isEqualTo("http")
            prop(HttpClientConfiguration::host).isEqualTo(hostname)
            prop(HttpClientConfiguration::port).isEqualTo(8080)
            prop(HttpClientConfiguration::contextPath).isEqualTo("")
            prop(HttpClientConfiguration::isSecure).isFalse()
        }
    }

    @Test
    internal fun `should configure the URL with authentication`() {
        // given
        val config = HttpClientConfiguration()

        // when
        config.url("http://my-user:my-password@$hostname:8080")

        // then
        assertThat(config).all {
            prop(HttpClientConfiguration::scheme).isEqualTo("http")
            prop(HttpClientConfiguration::host).isEqualTo(hostname)
            prop(HttpClientConfiguration::port).isEqualTo(8080)
            prop(HttpClientConfiguration::contextPath).isEqualTo("")
            prop(HttpClientConfiguration::isSecure).isFalse()
        }
    }
}
