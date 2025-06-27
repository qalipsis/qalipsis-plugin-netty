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

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
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
    ConfigurableStepSpecification<INPUT, UdpResult<INPUT, ByteArray>, UdpClientStepSpecification<INPUT>>,
    NettyPluginSpecification<INPUT, UdpResult<INPUT, ByteArray>, UdpClientStepSpecification<INPUT>> {

    internal var requestFactory: suspend (StepContext<*, *>, INPUT) -> ByteArray = { _, _ -> ByteArray(0) }

    internal val connectionConfiguration = ConnectionConfiguration()

    internal val monitoringConfiguration = StepMonitoringConfiguration()

    fun request(requestFactory: suspend (StepContext<*, *>, INPUT) -> ByteArray) {
        this.requestFactory = requestFactory
    }

    fun connect(configurationBlock: ConnectionConfiguration.() -> Unit) {
        connectionConfiguration.configurationBlock()
    }

    fun monitoring(configurationBlock: StepMonitoringConfiguration.() -> Unit) {
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
