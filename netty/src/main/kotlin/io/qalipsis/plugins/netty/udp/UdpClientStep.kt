package io.qalipsis.plugins.netty.udp

import io.micrometer.core.instrument.MeterRegistry
import io.netty.channel.EventLoopGroup
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.configuration.ConnectionConfiguration
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Step to send and receive data using UDP.
 *
 * @author Eric Jessé
 */
internal class UdpClientStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val ioCoroutineContext: CoroutineContext,
    private val requestFactory: suspend (StepContext<*, *>, I) -> ByteArray,
    private val connectionConfiguration: ConnectionConfiguration,
    private val eventLoopGroupSupplier: EventLoopGroupSupplier,
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: MeterRegistry?
) : AbstractStep<I, UdpResult<I, ByteArray>>(id, retryPolicy) {

    private lateinit var workerGroup: EventLoopGroup

    override suspend fun start(context: StepStartStopContext) {
        super.start(context)
        workerGroup = eventLoopGroupSupplier.getGroup()
    }

    override suspend fun stop(context: StepStartStopContext) {
        super.stop(context)
        workerGroup.shutdownGracefully()
    }

    override suspend fun execute(context: StepContext<I, UdpResult<I, ByteArray>>) {
        val monitoringCollector = UdpMonitoringCollector(context, eventsLogger, meterRegistry, "udp")
        val input = context.receive()

        val client = UdpClient()
        client.open(connectionConfiguration, workerGroup, monitoringCollector)

        val udpResult = try {
            val request = requestFactory(context, input)
            val response = withContext(ioCoroutineContext) {
                client.execute(context, request, monitoringCollector)
            }
            monitoringCollector.toResult(input, response, null)
        } catch (e: Exception) {
            // The exception is only considered if not already set in the context.
            throw UdpException(
                monitoringCollector.toResult(input, null, e.takeUnless { monitoringCollector.cause === e })
            )
        }

        if (udpResult.isFailure) {
            throw UdpException(udpResult)
        }

        context.send(udpResult)
    }

}
