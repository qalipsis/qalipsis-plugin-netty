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
            val step = CloseHttpClientStep<String>("", simpleHttpClientStep)

            step.discard(listOf("minion-1", "minion-2"))

            coVerifyOnce {
                simpleHttpClientStep.close(eq("minion-1"))
                simpleHttpClientStep.close(eq("minion-2"))
            }
            confirmVerified(simpleHttpClientStep)
        }

    @Test
    internal fun `should not forward the input when there is none`() = testDispatcherProvider.runTest {
        val step = CloseHttpClientStep<String>("", simpleHttpClientStep)
        val ctx = StepTestHelper.createStepContext<String, String>()
        ctx.isTail = true

        step.execute(ctx)

        Assertions.assertTrue((ctx.output as Channel).isEmpty)
        confirmVerified(simpleHttpClientStep)
    }

    @Test
    internal fun `should forward the input when there is one`() =
        testDispatcherProvider.runTest {
            val step = CloseHttpClientStep<String>("", simpleHttpClientStep)
            val ctx = StepTestHelper.createStepContext<String, String>(input = "This is a test")
            ctx.isTail = true

            step.execute(ctx)

            val output = (ctx.output as Channel).receive().value
            Assertions.assertEquals("This is a test", output)
            confirmVerified(simpleHttpClientStep)
        }
}
