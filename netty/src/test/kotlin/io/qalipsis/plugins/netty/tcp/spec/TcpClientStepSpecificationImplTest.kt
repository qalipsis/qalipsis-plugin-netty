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

package io.qalipsis.plugins.netty.tcp.spec

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
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.netty.ByteArrayRequestBuilder
import io.qalipsis.plugins.netty.configuration.TlsConfiguration
import io.qalipsis.plugins.netty.netty
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.Inet4Address
import java.net.Inet6Address

/**
 * @author Eric Jessé
 */
internal class TcpClientStepSpecificationImplTest {

    @Nested
    internal class `Standard TCP step` {

        @Test
        internal fun `should add minimal tcp step as next`() {
            val previousStep = DummyStepSpecification()
            val requestSpecification: suspend ByteArrayRequestBuilder.(StepContext<*, *>, Int) -> ByteArray =
                { _, _ -> ByteArray(1) { it.toByte() } }
            previousStep.netty().tcp {
                request(requestSpecification)
                connect {
                    address("localhost", 12234)
                }
            }

            assertThat(previousStep.nextSteps[0]).isInstanceOf(TcpClientStepSpecificationImpl::class).all {
                prop(TcpClientStepSpecificationImpl<*>::requestFactory).isSameAs(requestSpecification)
                prop(TcpClientStepSpecificationImpl<*>::poolConfiguration).isNull()
                prop(TcpClientStepSpecificationImpl<*>::connectionConfiguration).all {
                    prop(TcpClientConfiguration::host).isEqualTo("localhost")
                    prop(TcpClientConfiguration::port).isEqualTo(12234)
                    prop(TcpClientConfiguration::tlsConfiguration).isNull()
                    prop(TcpClientConfiguration::proxyConfiguration).isNull()
                }
                prop(TcpClientStepSpecificationImpl<*>::monitoringConfiguration).all {
                    prop(StepMonitoringConfiguration::events).isFalse()
                    prop(StepMonitoringConfiguration::meters).isFalse()
                }
            }
        }

        @Test
        internal fun `should add tcp step with pool as next using addresses as string and int`() {
            val previousStep = DummyStepSpecification()
            val requestSpecification: suspend ByteArrayRequestBuilder.(StepContext<*, *>, Int) -> ByteArray =
                { _, _ -> ByteArray(1) { it.toByte() } }
            previousStep.netty().tcp {
                request(requestSpecification)
                connect {
                    address("localhost", 12234)

                    tls {
                        disableCertificateVerification = true
                    }

                    proxy {
                        type = TcpProxyType.SOCKS5
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

            assertThat(previousStep.nextSteps[0]).isInstanceOf(TcpClientStepSpecificationImpl::class).all {
                prop(TcpClientStepSpecificationImpl<*>::requestFactory).isSameAs(requestSpecification)
                prop(TcpClientStepSpecificationImpl<*>::connectionConfiguration).all {
                    prop(TcpClientConfiguration::host).isEqualTo("localhost")
                    prop(TcpClientConfiguration::port).isEqualTo(12234)
                    prop(TcpClientConfiguration::tlsConfiguration).isNotNull().all {
                        prop(TlsConfiguration::disableCertificateVerification).isTrue()
                    }
                    prop(TcpClientConfiguration::proxyConfiguration).isNotNull().all {
                        prop(TcpProxyConfiguration::type).isEqualTo(TcpProxyType.SOCKS5)
                        prop(TcpProxyConfiguration::host).isEqualTo("my-proxy")
                        prop(TcpProxyConfiguration::port).isEqualTo(9876)
                    }
                }
                prop(TcpClientStepSpecificationImpl<*>::poolConfiguration).isNotNull().all {
                    prop(SocketClientPoolConfiguration::size).isEqualTo(143)
                    prop(SocketClientPoolConfiguration::checkHealthBeforeUse).isEqualTo(true)
                }
                prop(TcpClientStepSpecificationImpl<*>::monitoringConfiguration).all {
                    prop(StepMonitoringConfiguration::events).isTrue()
                    prop(StepMonitoringConfiguration::meters).isTrue()
                }
            }
        }

        @Test
        internal fun `should add tcp step as next using addresses as InetAddress and default proxy type`() {
            val previousStep = DummyStepSpecification()
            val requestSpecification: suspend ByteArrayRequestBuilder.(StepContext<*, *>, Int) -> ByteArray =
                { _, _ -> ByteArray(1) { it.toByte() } }
            val connectionAddress = Inet4Address.getLoopbackAddress()
            val proxyAddress = Inet6Address.getLoopbackAddress()
            previousStep.netty().tcp {
                request(requestSpecification)
                connect {
                    address(connectionAddress, 12234)
                    proxy {
                        address(proxyAddress, 9876)
                    }
                }
            }

            assertThat(previousStep.nextSteps[0]).isInstanceOf(TcpClientStepSpecificationImpl::class).all {
                prop(TcpClientStepSpecificationImpl<*>::requestFactory).isSameAs(requestSpecification)
                prop(TcpClientStepSpecificationImpl<*>::connectionConfiguration).all {
                    prop(TcpClientConfiguration::host).isEqualTo("127.0.0.1")
                    prop(TcpClientConfiguration::port).isEqualTo(12234)
                    prop(TcpClientConfiguration::tlsConfiguration).isNull()
                    prop(TcpClientConfiguration::proxyConfiguration).isNotNull().all {
                        prop(TcpProxyConfiguration::type).isEqualTo(TcpProxyType.SOCKS4)
                        prop(TcpProxyConfiguration::host).isEqualTo("127.0.0.1")
                        prop(TcpProxyConfiguration::port).isEqualTo(9876)
                    }
                }
                prop(TcpClientStepSpecificationImpl<*>::monitoringConfiguration).all {
                    prop(StepMonitoringConfiguration::events).isFalse()
                    prop(StepMonitoringConfiguration::meters).isFalse()
                }
            }
        }

        @Test
        internal fun `should add tcp step to scenario`() {
            val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
            val requestSpecification: suspend ByteArrayRequestBuilder.(StepContext<*, *>, Unit) -> ByteArray =
                { _, _ -> ByteArray(1) { it.toByte() } }
            scenario.netty().tcp {
                request(requestSpecification)
                connect {
                    address("localhost", 12234)
                }
            }

            assertThat(scenario.rootSteps[0]).isInstanceOf(TcpClientStepSpecificationImpl::class).all {
                prop(TcpClientStepSpecificationImpl<*>::requestFactory).isSameAs(requestSpecification)
                prop(TcpClientStepSpecificationImpl<*>::poolConfiguration).isNull()
                prop(TcpClientStepSpecificationImpl<*>::connectionConfiguration).all {
                    prop(TcpClientConfiguration::host).isEqualTo("localhost")
                    prop(TcpClientConfiguration::port).isEqualTo(12234)
                    prop(TcpClientConfiguration::tlsConfiguration).isNull()
                    prop(TcpClientConfiguration::proxyConfiguration).isNull()
                }
                prop(TcpClientStepSpecificationImpl<*>::monitoringConfiguration).all {
                    prop(StepMonitoringConfiguration::events).isFalse()
                    prop(StepMonitoringConfiguration::meters).isFalse()
                }
            }
        }
    }

    @Nested
    internal class `Reusing TCP connection step` {

        @Test
        internal fun `should add minimal reused tcp step as next`() {
            val previousStep = DummyStepSpecification()
            val requestSpecification: suspend ByteArrayRequestBuilder.(ctx: StepContext<*, *>, input: Int) -> ByteArray =
                { _, _ -> ByteArray(1) { it.toByte() } }
            previousStep.netty().tcpWith("my-step-to-reuse") {
                request(requestSpecification)
            }

            assertThat(previousStep.nextSteps[0]).isInstanceOf(QueryTcpClientStepSpecification::class).all {
                prop(QueryTcpClientStepSpecification<*>::stepName).isEqualTo("my-step-to-reuse")
                prop(QueryTcpClientStepSpecification<*>::requestFactory).isSameAs(requestSpecification)
                prop(QueryTcpClientStepSpecification<*>::monitoringConfiguration).all {
                    prop(StepMonitoringConfiguration::events).isFalse()
                    prop(StepMonitoringConfiguration::meters).isFalse()
                }
            }
        }

        @Test
        internal fun `should add reused tcp step as next`() {
            val previousStep = DummyStepSpecification()
            val requestSpecification: suspend ByteArrayRequestBuilder.(ctx: StepContext<*, *>, input: Int) -> ByteArray =
                { _, _ -> ByteArray(1) { it.toByte() } }
            previousStep.netty().tcpWith("my-step-to-reuse") {
                request(requestSpecification)

                monitoring {
                    events = true
                    meters = true
                }
            }

            assertThat(previousStep.nextSteps[0]).isInstanceOf(QueryTcpClientStepSpecification::class).all {
                prop(QueryTcpClientStepSpecification<*>::stepName).isEqualTo("my-step-to-reuse")
                prop(QueryTcpClientStepSpecification<*>::requestFactory).isSameAs(requestSpecification)
                prop(QueryTcpClientStepSpecification<*>::monitoringConfiguration).all {
                    prop(StepMonitoringConfiguration::events).isTrue()
                    prop(StepMonitoringConfiguration::meters).isTrue()
                }
            }
        }
    }

    @Nested
    internal class `Closing TCP connection step` {

        @Test
        internal fun `should add minimal reused tcp step as next`() {
            val previousStep = DummyStepSpecification()
            previousStep.netty().closeTcp("my-step-to-reuse")

            assertThat(previousStep.nextSteps[0]).isInstanceOf(CloseTcpClientStepSpecification::class).all {
                prop(CloseTcpClientStepSpecification<*>::stepName).isEqualTo("my-step-to-reuse")
            }
        }
    }

}
