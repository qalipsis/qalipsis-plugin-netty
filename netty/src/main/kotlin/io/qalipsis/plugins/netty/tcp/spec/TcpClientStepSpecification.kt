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

package io.qalipsis.plugins.netty.tcp.spec

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.netty.ByteArrayRequestBuilder
import io.qalipsis.plugins.netty.NettyPluginSpecification
import io.qalipsis.plugins.netty.NettyScenarioSpecification
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.tcp.ConnectionAndRequestResult

interface TcpClientStepSpecification<INPUT> :
    NettyPluginSpecification<INPUT, ConnectionAndRequestResult<INPUT, ByteArray>, TcpClientStepSpecification<INPUT>>,
    ConfigurableStepSpecification<INPUT, ConnectionAndRequestResult<INPUT, ByteArray>, TcpClientStepSpecification<INPUT>> {

    /**
     * Configures the creation of the payload to send to the remote address, using the [StepContext] and the input received
     * from the previous step.
     */
    fun request(requestFactory: suspend ByteArrayRequestBuilder.(StepContext<*, *>, INPUT) -> ByteArray)

    /**
     * Configures the connection to the remote address.
     */
    fun connect(configurationBlock: TcpClientConfiguration.() -> Unit)

    /**
     * Enables the creation of a pool of connections instead of one connection by minion.
     * This optimizes the throughput, when the remote server does not require a unique connection by client or user.
     */
    fun pool(configurationBlock: SocketClientPoolConfiguration.() -> Unit)

    /**
     * Configures the monitoring of the step.
     */
    fun monitoring(configurationBlock: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Specification for a [TcpClientStep].
 *
 * @author Eric Jessé
 */
@Spec
internal class TcpClientStepSpecificationImpl<INPUT> :
    AbstractStepSpecification<INPUT, ConnectionAndRequestResult<INPUT, ByteArray>, TcpClientStepSpecification<INPUT>>(),
    TcpClientStepSpecification<INPUT> {

    var requestFactory: suspend ByteArrayRequestBuilder.(StepContext<*, *>, INPUT) -> ByteArray =
        { _, _ -> ByteArray(0) }

    val connectionConfiguration = TcpClientConfiguration()

    var poolConfiguration: SocketClientPoolConfiguration? = null

    val monitoringConfiguration = StepMonitoringConfiguration()

    override fun request(requestFactory: suspend ByteArrayRequestBuilder.(StepContext<*, *>, INPUT) -> ByteArray) {
        this.requestFactory = requestFactory
    }

    override fun connect(configurationBlock: TcpClientConfiguration.() -> Unit) {
        connectionConfiguration.configurationBlock()
    }

    override fun pool(configurationBlock: SocketClientPoolConfiguration.() -> Unit) {
        this.poolConfiguration = SocketClientPoolConfiguration()
            .also { it.configurationBlock() }
    }

    override fun monitoring(configurationBlock: StepMonitoringConfiguration.() -> Unit) {
        monitoringConfiguration.configurationBlock()
    }
}

/**
 * Create a new TCP connection to send requests to a remote address.
 * It is not necessary to explicitly close the TCP connection after use if the workflow is straightforward.
 *
 * @see tcpWith
 * @see closeTcp
 *
 * @author Eric Jessé
 */
fun <INPUT> NettyPluginSpecification<*, INPUT, *>.tcp(
    configurationBlock: TcpClientStepSpecification<INPUT>.() -> Unit
): TcpClientStepSpecification<INPUT> {
    val step = TcpClientStepSpecificationImpl<INPUT>()
    step.configurationBlock()
    this.add(step)
    return step
}

/**
 * Create a new TCP connection to send requests to a remote address.
 * It is not necessary to explicitly close the TCP connection after use if the workflow is straightforward.
 *
 * @see tcpWith
 * @see closeTcp
 *
 * @author Eric Jessé
 */
fun NettyScenarioSpecification.tcp(
    configurationBlock: TcpClientStepSpecification<Unit>.() -> Unit
): TcpClientStepSpecification<Unit> {
    val step = TcpClientStepSpecificationImpl<Unit>()
    step.configurationBlock()
    (this as StepSpecificationRegistry).add(step)
    return step
}

/**
 * Specification for a [io.qalipsis.plugins.netty.tcp.QueryTcpClientStep].
 *
 * @author Eric Jessé
 */
@Spec
class QueryTcpClientStepSpecification<INPUT>(val stepName: String) :
    AbstractStepSpecification<INPUT, RequestResult<INPUT, ByteArray, *>, QueryTcpClientStepSpecification<INPUT>>(),
    NettyPluginSpecification<INPUT, RequestResult<INPUT, ByteArray, *>, QueryTcpClientStepSpecification<INPUT>> {

    internal var requestFactory: suspend ByteArrayRequestBuilder.(StepContext<*, *>, INPUT) -> ByteArray =
        { _, _ -> ByteArray(0) }

    internal val monitoringConfiguration = StepMonitoringConfiguration()

    fun request(requestBlock: suspend ByteArrayRequestBuilder.(StepContext<*, *>, input: INPUT) -> ByteArray) {
        this.requestFactory = requestBlock
    }

    fun monitoring(configurationBlock: StepMonitoringConfiguration.() -> Unit) {
        monitoringConfiguration.configurationBlock()
    }

}

/**
 * Reuses the connection created in the [tcp] step [stepName] and to perform new requests to the remote address.
 * It is not necessary to explicitly close the TCP connection after use if the workflow is straightforward.
 *
 * @param stepName name of the step where the TCP connection was open.
 *
 * @see tcp
 * @see closeTcp
 *
 * @author Eric Jessé
 */
fun <INPUT> NettyPluginSpecification<*, INPUT, *>.tcpWith(
    stepName: String,
    configurationBlock: QueryTcpClientStepSpecification<INPUT>.() -> Unit
): QueryTcpClientStepSpecification<INPUT> {
    val step = QueryTcpClientStepSpecification<INPUT>(stepName)
    step.configurationBlock()
    this.add(step)
    return step
}

@Spec
data class CloseTcpClientStepSpecification<INPUT>(val stepName: String) :
    AbstractStepSpecification<INPUT, INPUT, CloseTcpClientStepSpecification<INPUT>>(),
    NettyPluginSpecification<INPUT, INPUT, CloseTcpClientStepSpecification<INPUT>>

/**
 * Keep a previously created TCP connection open until that point.
 *
 *@param stepName name of the step where the TCP connection was open.
 *
 * @author Eric Jessé
 */
fun <INPUT> NettyPluginSpecification<*, INPUT, *>.closeTcp(stepName: String): CloseTcpClientStepSpecification<INPUT> {
    val step = CloseTcpClientStepSpecification<INPUT>(stepName)
    this.add(step)
    return step
}
