package io.qalipsis.plugins.netty.tcp

import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class CloseTcpClientStepTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var simpleTcpClientStep: SimpleTcpClientStep<*>

    @Test
    internal fun `should close the actual tcp step when the tail is found and forward the input`() =
        testDispatcherProvider.runTest {
            val step = CloseTcpClientStep<String>("", this.coroutineContext, simpleTcpClientStep)

            step.discard(listOf("minionId-1", "minionId-2"))

            coVerifyOnce {
                simpleTcpClientStep.close(eq("minionId-1"))
                simpleTcpClientStep.close(eq("minionId-2"))
            }
            confirmVerified(simpleTcpClientStep)
        }

    @Test
    internal fun `should not forward the input when there is none`() = testDispatcherProvider.runTest {
        val step = CloseTcpClientStep<String>("", this.coroutineContext, simpleTcpClientStep)
        val ctx = StepTestHelper.createStepContext<String, String>()
        ctx.isTail = true

        step.execute(ctx)

        Assertions.assertTrue((ctx.output as Channel).isEmpty)
        confirmVerified(simpleTcpClientStep)
    }


    @Test
    internal fun `should forward the input when there is one`() =
        testDispatcherProvider.runTest {
            val step = CloseTcpClientStep<String>("", this.coroutineContext, simpleTcpClientStep)
            val ctx = StepTestHelper.createStepContext<String, String>(input = "This is a test")
            ctx.isTail = true

            step.execute(ctx)

            val output = (ctx.output as Channel).receive().value
            Assertions.assertEquals("This is a test", output)
            confirmVerified(simpleTcpClientStep)
        }
}
