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
        val multiSocketHttpClient = MultiSocketHttpClient(1)
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
