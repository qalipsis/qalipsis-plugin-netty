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
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.plugins.netty.Monitoring
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
                prop(Monitoring::events).isFalse()
                prop(Monitoring::meters).isFalse()
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
                prop(Monitoring::events).isTrue()
                prop(Monitoring::meters).isFalse()
            }
        }
    }

    @Test
    internal fun `should add udp step to scenario`() {
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
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
                prop(Monitoring::events).isFalse()
                prop(Monitoring::meters).isTrue()
            }
        }
    }

}