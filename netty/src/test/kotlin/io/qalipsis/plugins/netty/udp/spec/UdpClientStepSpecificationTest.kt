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

package io.qalipsis.plugins.netty.udp.spec

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.netty.configuration.ConnectionConfiguration
import io.qalipsis.plugins.netty.netty
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class UdpClientStepSpecificationTest {

    @Test
    internal fun `should add minimal udp step as next`() {
        val previousStep = DummyStepSpecification()
        val requestSpecification: suspend (ctx: StepContext<*, *>, input: Int) -> ByteArray =
            { _, _ -> ByteArray(1) { it.toByte() } }
        previousStep.netty().udp {
            request(requestSpecification)
            connect {
                address("localhost", 12234)
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(UdpClientStepSpecification::class).all {
            prop(UdpClientStepSpecification<*>::requestFactory).isSameAs(requestSpecification)
            prop(UdpClientStepSpecification<*>::connectionConfiguration).all {
                prop(ConnectionConfiguration::host).isEqualTo("localhost")
                prop(ConnectionConfiguration::port).isEqualTo(12234)
            }
            prop(UdpClientStepSpecification<*>::monitoringConfiguration).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
        }
    }

    @Test
    internal fun `should add udp step as next using addresses as string and int`() {
        val previousStep = DummyStepSpecification()
        val requestSpecification: suspend (ctx: StepContext<*, *>, input: Int) -> ByteArray =
            { _, _ -> ByteArray(1) { it.toByte() } }
        previousStep.netty().udp {
            request(requestSpecification)
            connect {
                address("localhost", 12234)
            }
            monitoring {
                events = true
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(UdpClientStepSpecification::class).all {
            prop(UdpClientStepSpecification<*>::requestFactory).isSameAs(requestSpecification)
            prop(UdpClientStepSpecification<*>::connectionConfiguration).all {
                prop(ConnectionConfiguration::host).isEqualTo("localhost")
                prop(ConnectionConfiguration::port).isEqualTo(12234)
            }
            prop(UdpClientStepSpecification<*>::monitoringConfiguration).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
        }
    }

    @Test
    internal fun `should add udp step to scenario`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        val requestSpecification: suspend (ctx: StepContext<*, *>, input: Unit) -> ByteArray =
            { _, _ -> ByteArray(1) { it.toByte() } }
        scenario.netty().udp {
            request(requestSpecification)
            connect {
                address("localhost", 12234)
            }
            monitoring {
                meters = true
            }
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(UdpClientStepSpecification::class).all {
            prop(UdpClientStepSpecification<*>::requestFactory).isSameAs(requestSpecification)
            prop(UdpClientStepSpecification<*>::connectionConfiguration).all {
                prop(ConnectionConfiguration::host).isEqualTo("localhost")
                prop(ConnectionConfiguration::port).isEqualTo(12234)
            }
            prop(UdpClientStepSpecification<*>::monitoringConfiguration).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

}
