package io.qalipsis.plugins.netty.udp.spec

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.plugins.netty.Monitoring
import io.qalipsis.plugins.netty.NettyPluginSpecification
import io.qalipsis.plugins.netty.NettyScenarioSpecification
import io.qalipsis.plugins.netty.configuration.ConnectionConfiguration
import io.qalipsis.plugins.netty.udp.UdpResult

/**
 * Specification for a [UdpClientStep].
 *
 * @author Eric Jessé
 */
@Spec
class UdpClientStepSpecification<INPUT> :
    AbstractStepSpecification<INPUT, UdpResult<INPUT, ByteArray>, UdpClientStepSpecification<INPUT>>(),
    NettyPluginSpecification<INPUT, UdpResult<INPUT, ByteArray>, UdpClientStepSpecification<INPUT>> {

    internal var requestFactory: suspend (StepContext<*, *>, INPUT) -> ByteArray = { _, _ -> ByteArray(0) }

    internal val connectionConfiguration = ConnectionConfiguration()

    internal val monitoringConfiguration = Monitoring()

    fun request(requestFactory: suspend (StepContext<*, *>, INPUT) -> ByteArray) {
        this.requestFactory = requestFactory
    }

    fun connect(configurationBlock: ConnectionConfiguration.() -> Unit) {
        connectionConfiguration.configurationBlock()
    }

    fun monitoring(configurationBlock: Monitoring.() -> Unit) {
        monitoringConfiguration.configurationBlock()
    }
}

fun <INPUT> NettyPluginSpecification<*, INPUT, *>.udp(
    configurationBlock: UdpClientStepSpecification<INPUT>.() -> Unit
): UdpClientStepSpecification<INPUT> {
    val step = UdpClientStepSpecification<INPUT>()
    step.configurationBlock()
    this.add(step)
    return step
}

fun NettyScenarioSpecification.udp(
    configurationBlock: UdpClientStepSpecification<Unit>.() -> Unit
): UdpClientStepSpecification<Unit> {
    val step = UdpClientStepSpecification<Unit>()
    step.configurationBlock()
    (this as StepSpecificationRegistry).add(step)
    return step
}
