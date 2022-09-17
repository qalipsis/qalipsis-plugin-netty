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

package io.qalipsis.plugins.netty.http.client

import assertk.assertThat
import assertk.assertions.isEmpty
import io.aerisconsulting.catadioptre.getProperty
import io.mockk.coVerify
import io.qalipsis.api.sync.Slot
import io.qalipsis.plugins.netty.socket.SocketClient
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class MultiSocketHttpClientTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    internal fun `should close all the clients`() = testDispatcherProvider.run {
        // given
        val multiSocketHttpClient = MultiSocketHttpClient(1, this, this.coroutineContext)
        val clients: MutableMap<SocketClient.RemotePeerIdentifier, Slot<HttpClient>> =
            multiSocketHttpClient getProperty "clients"

        val client1: HttpClient = relaxedMockk()
        clients[relaxedMockk()] = Slot(client1)
        val client2: HttpClient = relaxedMockk()
        clients[relaxedMockk()] = Slot(client2)
        val client3: HttpClient = relaxedMockk()
        clients[relaxedMockk()] = Slot(client3)

        // when
        multiSocketHttpClient.close()

        // then
        assertThat(clients).isEmpty()
        coVerify {
            client1.close()
            client2.close()
            client3.close()
        }
    }
}
