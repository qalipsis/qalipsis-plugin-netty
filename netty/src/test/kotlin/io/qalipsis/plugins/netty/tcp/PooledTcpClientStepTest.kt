package io.qalipsis.plugins.netty.tcp

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.aerisconsulting.catadioptre.getProperty
import io.aerisconsulting.catadioptre.setProperty
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.netty.channel.EventLoopGroup
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.pool.FixedPool
import io.qalipsis.api.pool.Pool
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.RequestResult
import io.qalipsis.plugins.netty.monitoring.StepBasedTcpMonitoringCollector
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.tcp.client.TcpClient
import io.qalipsis.plugins.netty.tcp.spec.SocketClientPoolConfiguration
import io.qalipsis.plugins.netty.tcp.spec.TcpClientConfiguration
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.atomic.AtomicInteger

@WithMockk
internal class PooledTcpClientStepTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var configuration: TcpClientConfiguration

    @SpyK
    private var poolConfiguration = SocketClientPoolConfiguration(10, true)

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    private lateinit var workerGroupSupplier: EventLoopGroupSupplier

    @RelaxedMockK
    private lateinit var workerGroup: EventLoopGroup

    @Test
    fun `should create the pool at startup, clean at stop and create a new one at start again`() =
        testDispatcherProvider.run {
            // given
            val createdClientsCount = AtomicInteger()
            val step = spyk(
                PooledTcpClientStep<String>(
                    "my-step",
                    null,
                    this.coroutineContext,
                    { _, _ -> ByteArray(0) },
                    configuration,
                    poolConfiguration,
                    workerGroupSupplier,
                    eventsLogger,
                    meterRegistry
                )
            ) {
                coEvery { createClient(refEq(workerGroup)) } answers {
                    createdClientsCount.incrementAndGet()
                    relaxedMockk {
                        every { isOpen } returns true
                    }
                }
            }
            val eventsTags1 = relaxedMockk<Map<String, String>>()
            val meterTags1 = relaxedMockk<Tags>()
            val startStopContext1 = relaxedMockk<StepStartStopContext> {
                every { toEventTags() } returns eventsTags1
                every { toMetersTags() } returns meterTags1
            }
            every { workerGroupSupplier.getGroup() } returns workerGroup

            // when
            step.start(startStopContext1)
            assertThat(createdClientsCount.get()).isEqualTo(10)

            // then
            val fixedPool1 = step.getProperty<Pool<TcpClient>>("clientsPool")
            assertThat(step).typedProp<StepBasedTcpMonitoringCollector>("stepMonitoringCollector").all {
                prop("eventsLogger").isSameAs(eventsLogger)
                prop("meterRegistry").isSameAs(meterRegistry)
                prop("eventPrefix").isEqualTo("netty.tcp")
                prop("metersPrefix").isEqualTo("netty-tcp")
                prop("eventsTags").isSameAs(eventsTags1)
                prop("metersTags").isSameAs(meterTags1)
            }
            assertThat(step).prop("workerGroup").isSameAs(workerGroup)

            // when
            val mockedPool = relaxedMockk<Pool<TcpClient>>()
            step.setProperty("clientsPool", mockedPool)
            step.stop(startStopContext1)

            // then
            coVerifyOnce {
                mockedPool.close()
                workerGroup.shutdownGracefully()
            }

            // when
            val eventsTags2 = relaxedMockk<Map<String, String>>()
            val meterTags2 = relaxedMockk<Tags>()
            val startStopContext2 = relaxedMockk<StepStartStopContext> {
                every { toEventTags() } returns eventsTags2
                every { toMetersTags() } returns meterTags2
            }
            step.start(startStopContext2)
            assertThat(createdClientsCount.get()).isEqualTo(20)

            val fixedPool2 = step.getProperty<Pool<TcpClient>>("clientsPool")
            assertThat(fixedPool2).isInstanceOf(FixedPool::class).all {
                isNotSameAs(mockedPool)
                isNotSameAs(fixedPool1)
            }

            assertThat(step).typedProp<StepBasedTcpMonitoringCollector>("stepMonitoringCollector").all {
                prop("eventsLogger").isSameAs(eventsLogger)
                prop("meterRegistry").isSameAs(meterRegistry)
                prop("eventPrefix").isEqualTo("netty.tcp")
                prop("metersPrefix").isEqualTo("netty-tcp")
                prop("eventsTags").isSameAs(eventsTags2)
                prop("metersTags").isSameAs(meterTags2)
            }
        }

    @Test
    fun `should execute the request from the step context`() = testDispatcherProvider.run {
        // given
        val request = ByteArray(0)
        val response = ByteArray(0)
        val requestFactory: suspend (StepContext<*, *>, String) -> ByteArray = { _, _ -> request }
        val step = spyk(
            PooledTcpClientStep(
                "my-step",
                null,
                this.coroutineContext,
                requestFactory,
                configuration,
                poolConfiguration,
                workerGroupSupplier,
                eventsLogger,
                meterRegistry
            )
        ) {
            coEvery { execute<String>(any(), any(), any(), any()) } returns response
        }
        val ctx =
            spyk(StepTestHelper.createStepContext<String, RequestResult<String, ByteArray, *>>(input = "This is a test"))

        // when
        step.execute(ctx)

        // then
        val monitoringCollectorCaptor = slot<StepContextBasedSocketMonitoringCollector>()
        coVerify {
            step.execute(capture(monitoringCollectorCaptor), refEq(ctx), eq("This is a test"), refEq(request))
        }
        assertThat(monitoringCollectorCaptor.captured).all {
            prop("eventsLogger").isSameAs(eventsLogger)
            prop("meterRegistry").isSameAs(meterRegistry)
            prop("stepContext").isSameAs(ctx)
            prop("eventPrefix").isEqualTo("netty.tcp")
            prop("metersPrefix").isEqualTo("netty-tcp")
        }
        val resultCaptor = slot<RequestResult<String, ByteArray, *>>()
        coVerify { ctx.send(capture(resultCaptor)) }
        assertThat(resultCaptor.captured).all {
            prop(RequestResult<String, ByteArray, *>::input).isEqualTo("This is a test")
            prop(RequestResult<String, ByteArray, *>::response).isSameAs(response)
        }
    }

    @Test
    fun `should execute on client`() = testDispatcherProvider.run {
        // given
        val request = ByteArray(0)
        val requestFactory: suspend (StepContext<*, *>, String) -> ByteArray = { _, _ -> request }
        val step = PooledTcpClientStep(
            "my-step",
            null,
            this.coroutineContext,
            requestFactory,
            configuration,
            poolConfiguration,
            workerGroupSupplier,
            eventsLogger,
            meterRegistry
        )

        val response = ByteArray(0)
        val client = relaxedMockk<TcpClient> {
            coEvery { execute<String>(any(), any(), any()) } returns response
        }
        relaxedMockk<Pool<TcpClient>> {
            coEvery { withPoolItem<ByteArray>(any()) } coAnswers {
                val block: (suspend (TcpClient) -> ByteArray) = firstArg()
                block(client)
            }
        }.also { step.setProperty("clientsPool", it) }
        val monitoringCollector = relaxedMockk<StepContextBasedSocketMonitoringCollector>()
        val ctx =
            spyk(StepTestHelper.createStepContext<String, RequestResult<String, ByteArray, *>>(input = "This is a test"))

        // when
        val result = step.execute(monitoringCollector, ctx, "This is a test", request)

        // then
        assertThat(result).isSameAs(response)
        coVerifyOnce {
            client.execute(refEq(ctx), refEq(request), refEq(monitoringCollector))
        }
    }

}
