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
