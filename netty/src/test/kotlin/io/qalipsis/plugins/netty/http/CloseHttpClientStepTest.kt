package io.qalipsis.plugins.netty.http

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
internal class CloseHttpClientStepTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var simpleHttpClientStep: SimpleHttpClientStep<*, *>

    @Test
    internal fun `should close the actual http step when the tail is found and forward the input`() =
        testDispatcherProvider.runTest {
            val step = CloseHttpClientStep<String>("", this.coroutineContext, simpleHttpClientStep)

            step.discard(listOf("minion-1", "minion-2"))

            coVerifyOnce {
                simpleHttpClientStep.close(eq("minion-1"))
                simpleHttpClientStep.close(eq("minion-2"))
            }
            confirmVerified(simpleHttpClientStep)
        }

    @Test
    internal fun `should not forward the input when there is none`() = testDispatcherProvider.runTest {
        val step = CloseHttpClientStep<String>("", this.coroutineContext, simpleHttpClientStep)
        val ctx = StepTestHelper.createStepContext<String, String>()
        ctx.isTail = true

        step.execute(ctx)

        Assertions.assertTrue((ctx.output as Channel).isEmpty)
        confirmVerified(simpleHttpClientStep)
    }

    @Test
    internal fun `should forward the input when there is one`() =
        testDispatcherProvider.runTest {
            val step = CloseHttpClientStep<String>("", this.coroutineContext, simpleHttpClientStep)
            val ctx = StepTestHelper.createStepContext<String, String>(input = "This is a test")
            ctx.isTail = true

            step.execute(ctx)

            val output = (ctx.output as Channel).receive().value
            Assertions.assertEquals("This is a test", output)
            confirmVerified(simpleHttpClientStep)
        }
}
