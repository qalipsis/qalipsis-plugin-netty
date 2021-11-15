package io.qalipsis.plugins.netty.tcp

import io.micrometer.core.instrument.MeterRegistry
import io.netty.channel.EventLoopGroup
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.socket.PooledSocketClientStep
import io.qalipsis.plugins.netty.tcp.client.TcpClient
import io.qalipsis.plugins.netty.tcp.spec.SocketClientPoolConfiguration
import io.qalipsis.plugins.netty.tcp.spec.TcpClientConfiguration
import kotlin.coroutines.CoroutineContext

/**
 * Step to send and receive data using TCP, using a connections pool.
 *
 * @author Eric Jessé
 */
internal class PooledTcpClientStep<I>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    private val ioCoroutineContext: CoroutineContext,
    requestFactory: suspend (StepContext<*, *>, I) -> ByteArray,
    private val clientConfiguration: TcpClientConfiguration,
    poolConfiguration: SocketClientPoolConfiguration,
    eventLoopGroupSupplier: EventLoopGroupSupplier,
    eventsLogger: EventsLogger?,
    meterRegistry: MeterRegistry?
) : PooledSocketClientStep<I, ByteArray, TcpClientConfiguration, ByteArray, ByteArray, TcpClient>(
    id,
    retryPolicy,
    ioCoroutineContext,
    requestFactory,
    poolConfiguration,
    "tcp",
    eventLoopGroupSupplier,
    eventsLogger,
    meterRegistry,
    ioCoroutineContext
), TcpClientStep<I, RequestResult<I, ByteArray, *>> {

    override suspend fun createClient(workerGroup: EventLoopGroup): TcpClient {
        val cli = TcpClient(Long.MAX_VALUE, ioCoroutineContext)
        cli.open(clientConfiguration, workerGroup, stepMonitoringCollector)
        return cli
    }
}

