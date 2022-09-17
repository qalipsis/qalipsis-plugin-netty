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

package io.qalipsis.plugins.netty.tcp

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.SimpleSocketClientStep
import io.qalipsis.plugins.netty.tcp.client.TcpClient
import io.qalipsis.plugins.netty.tcp.spec.TcpClientConfiguration
import kotlin.coroutines.CoroutineContext

/**
 * Step to send and receive data using TCP, using a per-minion connection strategy.
 *
 * @author Eric Jessé
 */
internal class SimpleTcpClientStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val ioCoroutineContext: CoroutineContext,
    requestFactory: suspend (StepContext<*, *>, I) -> ByteArray,
    private val clientConfiguration: TcpClientConfiguration,
    eventLoopGroupSupplier: EventLoopGroupSupplier,
    eventsLogger: EventsLogger?,
    meterRegistry: MeterRegistry?
) : SimpleSocketClientStep<I, ByteArray, TcpClientConfiguration, ByteArray, ByteArray, TcpClient>(
    id,
    retryPolicy,
    ioCoroutineContext,
    requestFactory,
    clientConfiguration,
    "tcp",
    eventLoopGroupSupplier,
    eventsLogger,
    meterRegistry
), TcpClientStep<I, ConnectionAndRequestResult<I, ByteArray>> {

    public override suspend fun createClient(
        minionId: MinionId,
        monitoringCollector: StepContextBasedSocketMonitoringCollector
    ): TcpClient {
        log.trace { "Creating the TCP client for minion $minionId" }
        val numberOfPlannedUsages =
            if (clientConfiguration.keepConnectionAlive) Long.MAX_VALUE else usagesCount.get().toLong()
        val cli = TcpClient(numberOfPlannedUsages, ioCoroutineContext) {
            // When closing, remove the client and the usage counter from the cache.
            clients.remove(minionId)?.close()
            clientsInUse.remove(minionId)
        }
        cli.open(clientConfiguration, workerGroup, monitoringCollector)
        return cli
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }

}

