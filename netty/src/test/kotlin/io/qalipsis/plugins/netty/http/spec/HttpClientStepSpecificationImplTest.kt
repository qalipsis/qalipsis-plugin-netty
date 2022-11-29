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
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.netty.handler.codec.http.HttpMethod
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.netty.configuration.TlsConfiguration
import io.qalipsis.plugins.netty.http.request.HttpRequest
import io.qalipsis.plugins.netty.http.request.SimpleHttpRequest
import io.qalipsis.plugins.netty.netty
import io.qalipsis.plugins.netty.tcp.spec.SocketClientPoolConfiguration
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class HttpClientStepSpecificationImplTest {

    @Nested
    internal class `Standard HTTP step` {

        @Test
        internal fun `should add minimal http step as next`() {
            val previousStep = DummyStepSpecification()
            val requestSpecification: suspend (ctx: StepContext<*, *>, input: Int) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head") }
            previousStep.netty().http {
                request(requestSpecification)
                connect {
                    url("http://localhost:12234")
                }
            }

            assertThat(previousStep.nextSteps[0]).isInstanceOf(HttpClientStepSpecificationImpl::class).all {
                prop(HttpClientStepSpecificationImpl<*, *>::requestFactory).isSameAs(requestSpecification)
                prop(HttpClientStepSpecificationImpl<*, *>::poolConfiguration).isNull()
                prop(HttpClientStepSpecificationImpl<*, *>::bodyType).isEqualTo(String::class)
                prop(HttpClientStepSpecificationImpl<*, *>::connectionConfiguration).all {
                    prop(HttpClientConfiguration::version).isEqualTo(HttpVersion.HTTP_1_1)
                    prop(HttpClientConfiguration::host).isEqualTo("localhost")
                    prop(HttpClientConfiguration::port).isEqualTo(12234)
                    prop(HttpClientConfiguration::scheme).isEqualTo("http")
                    prop(HttpClientConfiguration::contextPath).isEqualTo("")
                    prop(HttpClientConfiguration::connectTimeout).isEqualTo(Duration.ofSeconds(10))
                    prop(HttpClientConfiguration::noDelay).isTrue()
                    prop(HttpClientConfiguration::keepConnectionAlive).isTrue()
                    prop(HttpClientConfiguration::charset).isEqualTo(Charsets.UTF_8)
                    prop(HttpClientConfiguration::maxContentLength).isEqualTo(1048576)
                    prop(HttpClientConfiguration::inflate).isFalse()
                    prop(HttpClientConfiguration::followRedirections).isFalse()
                    prop(HttpClientConfiguration::maxRedirections).isEqualTo(10)
                    prop(HttpClientConfiguration::tlsConfiguration).isNull()
                    prop(HttpClientConfiguration::proxyConfiguration).isNull()
                }
                prop(HttpClientStepSpecificationImpl<*, *>::monitoringConfiguration).all {
                    prop(StepMonitoringConfiguration::events).isFalse()
                    prop(StepMonitoringConfiguration::meters).isFalse()
                }
            }
        }

        @Test
        internal fun `should add http step with pool as next using addresses as string and int`() {
            val previousStep = DummyStepSpecification()
            val requestSpecification: suspend (ctx: StepContext<*, *>, input: Int) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head") }
            previousStep.netty().http {
                request(requestSpecification)
                connect {
                    url("https://localhost:12234/test")
                    connectTimeout = Duration.ofMinutes(1)
                    inflate()
                    charset(Charsets.ISO_8859_1)
                    maxContentLength(123)
                    followRedirections(15)

                    noDelay = false
                    keepConnectionAlive = false

                    tls {
                        disableCertificateVerification = true
                    }

                    proxy {
                        type = HttpProxyType.SOCKS5
                        address("my-proxy", 9876)
                    }
                }

                pool {
                    size = 143
                    checkHealthBeforeUse = true
                }

                monitoring {
                    events = true
                    meters = true
                }
            }

            assertThat(previousStep.nextSteps[0]).isInstanceOf(HttpClientStepSpecificationImpl::class).all {
                prop(HttpClientStepSpecificationImpl<*, *>::requestFactory).isSameAs(requestSpecification)
                prop(HttpClientStepSpecificationImpl<*, *>::bodyType).isEqualTo(String::class)
                prop(HttpClientStepSpecificationImpl<*, *>::connectionConfiguration).all {
                    prop(HttpClientConfiguration::version).isEqualTo(HttpVersion.HTTP_1_1)
                    prop(HttpClientConfiguration::host).isEqualTo("localhost")
                    prop(HttpClientConfiguration::port).isEqualTo(12234)
                    prop(HttpClientConfiguration::scheme).isEqualTo("https")
                    prop(HttpClientConfiguration::contextPath).isEqualTo("/test")
                    prop(HttpClientConfiguration::connectTimeout).isEqualTo(Duration.ofMinutes(1))
                    prop(HttpClientConfiguration::noDelay).isFalse()
                    prop(HttpClientConfiguration::keepConnectionAlive).isFalse()
                    prop(HttpClientConfiguration::charset).isEqualTo(Charsets.ISO_8859_1)
                    prop(HttpClientConfiguration::maxContentLength).isEqualTo(123)
                    prop(HttpClientConfiguration::inflate).isTrue()
                    prop(HttpClientConfiguration::followRedirections).isTrue()
                    prop(HttpClientConfiguration::maxRedirections).isEqualTo(15)
                    prop(HttpClientConfiguration::tlsConfiguration).isNotNull().all {
                        prop(TlsConfiguration::disableCertificateVerification).isTrue()
                    }
                    prop(HttpClientConfiguration::proxyConfiguration).isNotNull().all {
                        prop(HttpProxyConfiguration::type).isEqualTo(HttpProxyType.SOCKS5)
                        prop(HttpProxyConfiguration::host).isEqualTo("my-proxy")
                        prop(HttpProxyConfiguration::port).isEqualTo(9876)
                    }
                }
                prop(HttpClientStepSpecificationImpl<*, *>::poolConfiguration).isNotNull().all {
                    prop(SocketClientPoolConfiguration::size).isEqualTo(143)
                    prop(SocketClientPoolConfiguration::checkHealthBeforeUse).isEqualTo(true)
                }
                prop(HttpClientStepSpecificationImpl<*, *>::monitoringConfiguration).all {
                    prop(StepMonitoringConfiguration::events).isTrue()
                    prop(StepMonitoringConfiguration::meters).isTrue()
                }
            }
        }

        @Test
        internal fun `should add http step to scenario`() {
            val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
            val requestSpecification: suspend (ctx: StepContext<*, *>, input: Unit) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head") }
            scenario.netty().http {
                request(requestSpecification)
                connect {
                    url("http://localhost:12234")
                }
            }.deserialize(Entity::class)

            assertThat(scenario.rootSteps[0]).isInstanceOf(HttpClientStepSpecificationImpl::class).all {
                prop(HttpClientStepSpecificationImpl<*, *>::requestFactory).isSameAs(requestSpecification)
                prop(HttpClientStepSpecificationImpl<*, *>::poolConfiguration).isNull()
                prop(HttpClientStepSpecificationImpl<*, *>::bodyType).isEqualTo(Entity::class)
                prop(HttpClientStepSpecificationImpl<*, *>::connectionConfiguration).all {
                    prop(HttpClientConfiguration::version).isEqualTo(HttpVersion.HTTP_1_1)
                    prop(HttpClientConfiguration::host).isEqualTo("localhost")
                    prop(HttpClientConfiguration::port).isEqualTo(12234)
                    prop(HttpClientConfiguration::scheme).isEqualTo("http")
                    prop(HttpClientConfiguration::contextPath).isEqualTo("")
                    prop(HttpClientConfiguration::connectTimeout).isEqualTo(Duration.ofSeconds(10))
                    prop(HttpClientConfiguration::noDelay).isTrue()
                    prop(HttpClientConfiguration::keepConnectionAlive).isTrue()
                    prop(HttpClientConfiguration::charset).isEqualTo(Charsets.UTF_8)
                    prop(HttpClientConfiguration::maxContentLength).isEqualTo(1048576)
                    prop(HttpClientConfiguration::inflate).isFalse()
                    prop(HttpClientConfiguration::followRedirections).isFalse()
                    prop(HttpClientConfiguration::maxRedirections).isEqualTo(10)
                    prop(HttpClientConfiguration::tlsConfiguration).isNull()
                    prop(HttpClientConfiguration::proxyConfiguration).isNull()
                }
                prop(HttpClientStepSpecificationImpl<*, *>::monitoringConfiguration).all {
                    prop(StepMonitoringConfiguration::events).isFalse()
                    prop(StepMonitoringConfiguration::meters).isFalse()
                }
            }
        }
    }

    @Nested
    internal class `Reusing HTTP connection step` {

        @Test
        internal fun `should add minimal reused http step as next`() {
            val previousStep = DummyStepSpecification()
            val requestSpecification: suspend (ctx: StepContext<*, *>, input: Int) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head") }
            previousStep.netty().httpWith("my-step-to-reuse") {
                request(requestSpecification)
            }

            assertThat(previousStep.nextSteps[0]).isInstanceOf(QueryHttpClientStepSpecification::class).all {
                prop(QueryHttpClientStepSpecification<*, *>::stepName).isEqualTo("my-step-to-reuse")
                prop(QueryHttpClientStepSpecification<*, *>::requestFactory).isSameAs(requestSpecification)
                prop(QueryHttpClientStepSpecification<*, *>::bodyType).isEqualTo(String::class)
                prop(QueryHttpClientStepSpecification<*, *>::monitoringConfiguration).all {
                    prop(StepMonitoringConfiguration::events).isFalse()
                    prop(StepMonitoringConfiguration::meters).isFalse()
                }
            }
        }

        @Test
        internal fun `should add reused http step as next`() {
            val previousStep = DummyStepSpecification()
            val requestSpecification: suspend (ctx: StepContext<*, *>, input: Int) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head") }
            previousStep.netty().httpWith("my-step-to-reuse") {
                request(requestSpecification)

                monitoring {
                    events = true
                    meters = true
                }
            }.deserialize(Entity::class)

            assertThat(previousStep.nextSteps[0]).isInstanceOf(QueryHttpClientStepSpecification::class).all {
                prop(QueryHttpClientStepSpecification<*, *>::stepName).isEqualTo("my-step-to-reuse")
                prop(QueryHttpClientStepSpecification<*, *>::requestFactory).isSameAs(requestSpecification)
                prop(QueryHttpClientStepSpecification<*, *>::bodyType).isEqualTo(Entity::class)
                prop(QueryHttpClientStepSpecification<*, *>::monitoringConfiguration).all {
                    prop(StepMonitoringConfiguration::events).isTrue()
                    prop(StepMonitoringConfiguration::meters).isTrue()
                }
            }
        }
    }

    @Nested
    internal class `Closing HTTP connection step` {

        @Test
        internal fun `should add minimal reused http step as next`() {
            val previousStep = DummyStepSpecification()
            previousStep.netty().closeHttp("my-step-to-reuse")

            assertThat(previousStep.nextSteps[0]).isInstanceOf(CloseHttpClientStepSpecification::class).all {
                prop(CloseHttpClientStepSpecification<*>::stepName).isEqualTo("my-step-to-reuse")
            }
        }
    }

    private data class Entity(val field: String)
}
