package io.qalipsis.plugins.netty.http

import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
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
            val ctx = StepTestHelper.createStepContext<String, String>(input = "This is a test")
            ctx.isTail = true

            step.execute(ctx)

            val output = (ctx.output as Channel).receive()
            Assertions.assertEquals("This is a test", output)
            coVerifyOnce {
                simpleHttpClientStep.close(refEq(ctx))
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
        coVerifyOnce {
            simpleHttpClientStep.close(refEq(ctx))
        }
        confirmVerified(simpleHttpClientStep)
    }

    @Test
    internal fun `should not close the actual http step when the the context is not the tail but forward the input`() =
        testDispatcherProvider.runTest {
            val step = CloseHttpClientStep<String>("", this.coroutineContext, simpleHttpClientStep)
            val ctx = StepTestHelper.createStepContext<String, String>(input = "This is a test")
            ctx.isTail = false

            step.execute(ctx)

            val output = (ctx.output as Channel).receive()
            Assertions.assertEquals("This is a test", output)
            coVerifyNever {
                simpleHttpClientStep.close(refEq(ctx))
            }
            confirmVerified(simpleHttpClientStep)
        }

    @Test
    internal fun `should not close the actual http step when the the context is not the tail but not block if there is no input`() =
        testDispatcherProvider.runTest {
            val step = CloseHttpClientStep<String>("", this.coroutineContext, simpleHttpClientStep)
            val ctx = StepTestHelper.createStepContext<String, String>()
            ctx.isTail = false

            step.execute(ctx)

            Assertions.assertTrue((ctx.output as Channel).isEmpty)
            coVerifyNever {
                simpleHttpClientStep.close(refEq(ctx))
            }
            confirmVerified(simpleHttpClientStep)
        }

    @Test
    internal fun `should close the actual http step when the tail is found`() = testDispatcherProvider.runTest {
        val step = CloseHttpClientStep<String>("", this.coroutineContext, simpleHttpClientStep)
        val ctx = StepTestHelper.createStepContext<String, String>(input = "This is a test")
        ctx.isTail = true
        ctx.isExhausted = false

        step.execute(ctx)

        val output = (ctx.output as Channel).receive()
        Assertions.assertEquals("This is a test", output)
        coVerifyOnce {
            simpleHttpClientStep.close(refEq(ctx))
        }
        confirmVerified(simpleHttpClientStep)
    }

    @Test
    internal fun `should close the actual http step when the tail is found and the context is exhausted`() =
        testDispatcherProvider.runTest {
            val step = CloseHttpClientStep<String>("", this.coroutineContext, simpleHttpClientStep)
            val ctx = StepTestHelper.createStepContext<String, String>(input = "This is a test")
            ctx.isTail = true
            ctx.isExhausted = true

            step.execute(ctx)

            val output = (ctx.output as Channel).receive()
            Assertions.assertEquals("This is a test", output)
            coVerifyOnce {
                simpleHttpClientStep.close(refEq(ctx))
            }
            confirmVerified(simpleHttpClientStep)
        }

    @Test
    internal fun `should not close the actual http step when the the context is not the tail`() =
        testDispatcherProvider.runTest {
            val step = CloseHttpClientStep<String>("", this.coroutineContext, simpleHttpClientStep)
            val ctx = StepTestHelper.createStepContext<String, String>(input = "This is a test")
            ctx.isTail = false
            ctx.isExhausted = true

            step.execute(ctx)

            val output = (ctx.output as Channel).receive()
            Assertions.assertEquals("This is a test", output)
            coVerifyNever {
                simpleHttpClientStep.close(refEq(ctx))
            }
            confirmVerified(simpleHttpClientStep)
        }

    @Test
    internal fun `should not return the error from the client but forward the input`() =
        testDispatcherProvider.runTest {
            coEvery { simpleHttpClientStep.close(any()) } throws RuntimeException()
            val step = CloseHttpClientStep<String>("", this.coroutineContext, simpleHttpClientStep)
            val ctx = StepTestHelper.createStepContext<String, String>(input = "This is a test")
            ctx.isTail = true
            ctx.isExhausted = false

            step.execute(ctx)

            val output = (ctx.output as Channel).receive()
            Assertions.assertEquals("This is a test", output)
            coVerifyOnce {
                simpleHttpClientStep.close(refEq(ctx))
            }
            confirmVerified(simpleHttpClientStep)
        }

    @Test
    internal fun `should not return the error from the client and not block if there is no input`() =
        testDispatcherProvider.runTest {
            coEvery { simpleHttpClientStep.close(any()) } throws RuntimeException()
            val step = CloseHttpClientStep<String>("", this.coroutineContext, simpleHttpClientStep)
            val ctx = StepTestHelper.createStepContext<String, String>()
            ctx.isTail = true
            ctx.isExhausted = false

            step.execute(ctx)

            Assertions.assertTrue((ctx.output as Channel).isEmpty)
            coVerifyOnce {
                simpleHttpClientStep.close(refEq(ctx))
            }
            confirmVerified(simpleHttpClientStep)
        }
}
