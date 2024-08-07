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

package io.qalipsis.plugins.netty.socket

import io.netty.channel.EventLoopGroup
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.pool.FixedPool
import io.qalipsis.api.pool.Pool
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.RequestBuilder
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.monitoring.StepBasedTcpMonitoringCollector
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.tcp.spec.SocketClientPoolConfiguration
import kotlin.coroutines.CoroutineContext

/**
 * Parent class of steps using a pool of implementations of [SocketClient]s.
 *
 * @param I type of the input of the step
 * @param O type of the output of the step
 * @param CONF type of the configuration of the client
 * @param REQ type of the request processed by the client (and generated from I)
 * @param RES type of the response generated by the client (and later converted to O)
 * @param CLI type of the client
 *
 * @author Eric Jessé
 */
internal abstract class PooledSocketClientStep<I, O, CONF : SocketClientConfiguration, REQ : Any, RES : Any, REQ_BUILDER : RequestBuilder<REQ>, CLI : SocketClient<CONF, REQ, RES, CLI>>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val requestBuilder: REQ_BUILDER,
    private val requestFactory: suspend REQ_BUILDER.(StepContext<*, *>, I) -> REQ,
    private val poolConfiguration: SocketClientPoolConfiguration,
    private val stepQualifier: String,
    private val eventLoopGroupSupplier: EventLoopGroupSupplier,
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: CampaignMeterRegistry?,
    private val coroutineContext: CoroutineContext
) : AbstractStep<I, RequestResult<I, RES, *>>(id, retryPolicy),
    SocketClientStep<I, REQ, RES, RequestResult<I, RES, *>> {

    private lateinit var clientsPool: Pool<CLI>

    protected lateinit var stepMonitoringCollector: StepBasedTcpMonitoringCollector

    private lateinit var workerGroup: EventLoopGroup

    override suspend fun start(context: StepStartStopContext) {
        stepMonitoringCollector = StepBasedTcpMonitoringCollector(eventsLogger, meterRegistry, context, stepQualifier)
        workerGroup = eventLoopGroupSupplier.getGroup()
        clientsPool = FixedPool(
            poolConfiguration.size,
            coroutineContext,
            poolConfiguration.checkHealthBeforeUse,
            true,
            healthCheck = { it.isOpen },
            factory = { createClient(workerGroup) }
        ).awaitReadiness()
    }

    internal abstract suspend fun createClient(workerGroup: EventLoopGroup): CLI

    override suspend fun stop(context: StepStartStopContext) {
        clientsPool.close()
        workerGroup.shutdownGracefully()
    }

    override suspend fun execute(context: StepContext<I, RequestResult<I, RES, *>>) {
        val monitoringCollector =
            StepContextBasedSocketMonitoringCollector(context, eventsLogger, meterRegistry, stepQualifier)
        val input = context.receive()
        val response = execute(monitoringCollector, context, input, requestBuilder.requestFactory(context, input))
        val result: RequestResult<I, RES, *> = monitoringCollector.toResult(input, response, null)
        context.send(result)
    }

    override suspend fun <IN> execute(
        monitoringCollector: StepContextBasedSocketMonitoringCollector,
        context: StepContext<*, *>,
        input: IN,
        request: REQ
    ): RES {
        return clientsPool.withPoolItem { client ->
            client.execute(context, request, monitoringCollector)
        }
    }

    /**
     * Converts the response from the client into the output of the step.
     */
    @Suppress("UNCHECKED_CAST")
    open fun convertResponseToOutput(response: RES): O = response as O

    /**
     * This has no effect on pooled steps.
     */
    override fun keepOpen() = Unit

    /**
     * This has no effect on pooled steps.
     */
    override suspend fun close(minionId: MinionId) = Unit

    /**
     * This has no effect on pooled steps.
     */
    override fun addUsage(count: Int) = Unit
}
