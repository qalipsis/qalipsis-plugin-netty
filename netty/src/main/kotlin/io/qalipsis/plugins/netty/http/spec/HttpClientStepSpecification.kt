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

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.netty.NettyPluginSpecification
import io.qalipsis.plugins.netty.NettyScenarioSpecification
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.http.HttpRequestBuilder
import io.qalipsis.plugins.netty.http.request.HttpRequest
import io.qalipsis.plugins.netty.http.response.HttpResponse
import io.qalipsis.plugins.netty.tcp.ConnectionAndRequestResult
import io.qalipsis.plugins.netty.tcp.spec.SocketClientPoolConfiguration
import kotlin.reflect.KClass

interface HttpClientStepSpecification<INPUT, OUTPUT> :
    NettyPluginSpecification<INPUT, ConnectionAndRequestResult<INPUT, HttpResponse<OUTPUT>>, HttpClientStepSpecification<INPUT, OUTPUT>>,
    ConfigurableStepSpecification<INPUT, ConnectionAndRequestResult<INPUT, HttpResponse<OUTPUT>>, HttpClientStepSpecification<INPUT, OUTPUT>> {

    /**
     * Configures the creation of the payload to send to the remote address, using the [StepContext] and the input received
     * from the previous step.
     */
    fun request(requestFactory: suspend HttpRequestBuilder.(StepContext<*, *>, INPUT) -> HttpRequest<*>)

    /**
     * Configures the connection to the remote address.
     */
    fun connect(configurationBlock: HttpClientConfiguration.() -> Unit)

    /**
     * Enables the creation of a pool of connections instead of one connection by minion.
     * This optimizes the throughput, when the remote server does not require a unique connection by client or user.
     */
    fun pool(configurationBlock: SocketClientPoolConfiguration.() -> Unit)

    /**
     * Configures the monitoring of the step.
     */
    fun monitoring(configurationBlock: StepMonitoringConfiguration.() -> Unit)

    /**
     * Deserialize the body into the defined type using the first [io.qalipsis.plugins.netty.http.response.HttpBodyDeserializer] matching the content type
     * of the response. Defaults implementations are proposed for JSON and XML, you can create your own implementations.
     */
    fun <T : Any> deserialize(type: KClass<T>): HttpClientStepSpecification<INPUT, T>
}

/**
 * Specification for a [HttpClientStep].
 *
 * @author Eric Jessé
 */
@Spec
internal class HttpClientStepSpecificationImpl<INPUT, OUTPUT> :
    AbstractStepSpecification<INPUT, ConnectionAndRequestResult<INPUT, HttpResponse<OUTPUT>>, HttpClientStepSpecification<INPUT, OUTPUT>>(),
    HttpClientStepSpecification<INPUT, OUTPUT> {

    lateinit var requestFactory: suspend HttpRequestBuilder.(StepContext<*, *>, INPUT) -> HttpRequest<*>

    var bodyType: KClass<*> = String::class

    val connectionConfiguration = HttpClientConfiguration()

    var poolConfiguration: SocketClientPoolConfiguration? = null

    val monitoringConfiguration = StepMonitoringConfiguration()

    override fun request(requestFactory: suspend HttpRequestBuilder.(StepContext<*, *>, INPUT) -> HttpRequest<*>) {
        this.requestFactory = requestFactory
    }

    override fun connect(configurationBlock: HttpClientConfiguration.() -> Unit) {
        connectionConfiguration.configurationBlock()
    }

    override fun pool(configurationBlock: SocketClientPoolConfiguration.() -> Unit) {
        this.poolConfiguration = SocketClientPoolConfiguration()
            .also { it.configurationBlock() }
    }

    override fun monitoring(configurationBlock: StepMonitoringConfiguration.() -> Unit) {
        monitoringConfiguration.configurationBlock()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> deserialize(type: KClass<T>): HttpClientStepSpecification<INPUT, T> {
        bodyType = type
        return this as HttpClientStepSpecification<INPUT, T>
    }
}

/**
 * Create a new HTTP connection to send requests to a remote address.
 * It is not necessary to explicitly close the HTTP connection after use if the workflow is straightforward.
 *
 * @see httpWith
 * @see closeHttp
 *
 * @author Eric Jessé
 */
fun <INPUT> NettyPluginSpecification<*, INPUT, *>.http(
    configurationBlock: HttpClientStepSpecification<INPUT, String>.() -> Unit
): HttpClientStepSpecification<INPUT, String> {
    val step = HttpClientStepSpecificationImpl<INPUT, String>()
    step.configurationBlock()
    this.add(step)
    return step
}

/**
 * Create a new HTTP connection to send requests to a remote address.
 * It is not necessary to explicitly close the HTTP connection after use if the workflow is straightforward.
 *
 * @see httpWith
 * @see closeHttp
 *
 * @author Eric Jessé
 */
fun NettyScenarioSpecification.http(
    configurationBlock: HttpClientStepSpecification<Unit, String>.() -> Unit
): HttpClientStepSpecification<Unit, String> {
    val step = HttpClientStepSpecificationImpl<Unit, String>()
    step.configurationBlock()
    (this as StepSpecificationRegistry).add(step)
    return step
}

/**
 * Specification for a [io.qalipsis.plugins.netty.http.QueryHttpClientStep].
 *
 * @author Eric Jessé
 */
@Spec
class QueryHttpClientStepSpecification<INPUT, OUTPUT>(val stepName: String) :
    AbstractStepSpecification<INPUT, RequestResult<INPUT, HttpResponse<OUTPUT>, *>, QueryHttpClientStepSpecification<INPUT, OUTPUT>>(),
    NettyPluginSpecification<INPUT, RequestResult<INPUT, HttpResponse<OUTPUT>, *>, QueryHttpClientStepSpecification<INPUT, OUTPUT>> {

    internal lateinit var requestFactory: suspend HttpRequestBuilder.(StepContext<*, *>, INPUT) -> HttpRequest<*>

    var bodyType: KClass<*> = String::class

    internal val monitoringConfiguration = StepMonitoringConfiguration()

    fun request(requestBlock: suspend HttpRequestBuilder.(StepContext<*, *>, input: INPUT) -> HttpRequest<*>) {
        this.requestFactory = requestBlock
    }

    fun monitoring(configurationBlock: StepMonitoringConfiguration.() -> Unit) {
        monitoringConfiguration.configurationBlock()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> deserialize(type: KClass<T>): QueryHttpClientStepSpecification<INPUT, T> {
        bodyType = type
        return this as QueryHttpClientStepSpecification<INPUT, T>
    }

}

/**
 * Reuses the connection created in the [http] step [stepName] and to perform new requests to the remote address.
 * It is not necessary to explicitly close the HTTP connection after use if the workflow is straightforward.
 *
 * @param stepName name of the step where the HTTP connection was open.
 *
 * @see http
 * @see closeHttp
 *
 * @author Eric Jessé
 */
fun <INPUT> NettyPluginSpecification<*, INPUT, *>.httpWith(
    stepName: String,
    configurationBlock: QueryHttpClientStepSpecification<INPUT, String>.() -> Unit
): QueryHttpClientStepSpecification<INPUT, String> {
    val step = QueryHttpClientStepSpecification<INPUT, String>(stepName)
    step.configurationBlock()
    this.add(step)
    return step
}

@Spec
data class CloseHttpClientStepSpecification<INPUT>(val stepName: String) :
    AbstractStepSpecification<INPUT, INPUT, CloseHttpClientStepSpecification<INPUT>>(),
    NettyPluginSpecification<INPUT, INPUT, CloseHttpClientStepSpecification<INPUT>>

/**
 * Keep a previously created HTTP connection open until that point.
 *
 *@param stepName name of the step where the HTTP connection was open.
 *
 * @author Eric Jessé
 */
fun <INPUT> NettyPluginSpecification<*, INPUT, *>.closeHttp(stepName: String): CloseHttpClientStepSpecification<INPUT> {
    val step = CloseHttpClientStepSpecification<INPUT>(stepName)
    this.add(step)
    return step
}
