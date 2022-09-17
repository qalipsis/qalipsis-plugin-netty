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

package io.qalipsis.plugins.netty.udp.spec

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
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
    ConfigurableStepSpecification<INPUT, UdpResult<INPUT, ByteArray>, UdpClientStepSpecification<INPUT>>,
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
