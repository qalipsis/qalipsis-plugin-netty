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
