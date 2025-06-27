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
            val step = CloseTcpClientStep<String>("", simpleTcpClientStep)

            step.discard(listOf("minionId-1", "minionId-2"))

            coVerifyOnce {
                simpleTcpClientStep.close(eq("minionId-1"))
                simpleTcpClientStep.close(eq("minionId-2"))
            }
            confirmVerified(simpleTcpClientStep)
        }

    @Test
    internal fun `should not forward the input when there is none`() = testDispatcherProvider.runTest {
        val step = CloseTcpClientStep<String>("", simpleTcpClientStep)
        val ctx = StepTestHelper.createStepContext<String, String>()
        ctx.isTail = true

        step.execute(ctx)

        Assertions.assertTrue((ctx.output as Channel).isEmpty)
        confirmVerified(simpleTcpClientStep)
    }


    @Test
    internal fun `should forward the input when there is one`() =
        testDispatcherProvider.runTest {
            val step = CloseTcpClientStep<String>("", simpleTcpClientStep)
            val ctx = StepTestHelper.createStepContext<String, String>(input = "This is a test")
            ctx.isTail = true

            step.execute(ctx)

            val output = (ctx.output as Channel).receive().value
            Assertions.assertEquals("This is a test", output)
            confirmVerified(simpleTcpClientStep)
        }
}
