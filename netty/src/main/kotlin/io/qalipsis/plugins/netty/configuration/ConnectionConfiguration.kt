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

package io.qalipsis.plugins.netty.configuration

import io.netty.channel.ChannelOption
import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.constraints.PositiveDuration
import java.net.InetAddress
import java.time.Duration
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Positive

/**
 *
 * @property readTimeout timeout duration to receive a response after a request
 * @property shutdownTimeout timeout duration to close an open connection
 * @property sendBufferSize size (in bytes) of the sending buffer, defaults to 1024
 * @property receiveBufferSize size (in bytes) of the receiving buffer, defaults to 1024
 * @property nettyChannelOptions additional netty options to overload the default
 */
@Spec
class ConnectionConfiguration internal constructor(
    @field:PositiveDuration var readTimeout: Duration = Duration.ofSeconds(10),
    @field:PositiveDuration var shutdownTimeout: Duration = Duration.ofSeconds(10),
    @field:Positive var sendBufferSize: Int = 1024,
    @field:Positive var receiveBufferSize: Int = 1024,
    internal var nettyChannelOptions: MutableMap<ChannelOption<*>, Any> = mutableMapOf()
) {

    internal var inetAddress = InetAddress.getLocalHost()

    @field:NotBlank
    internal var host: String = "localhost"

    @field:NotNull
    @field:Positive
    internal var port: Int = 0

    /**
     * Configures the remote host and port to send the requests.
     */
    fun address(host: String, port: Int) {
        this.inetAddress = InetAddress.getByName(host)
        this.host = host
        this.port = port
    }

    /**
     * Configures the remote address and port to send the requests.
     */
    fun address(address: InetAddress, port: Int) {
        this.inetAddress = address
        this.host = address.hostAddress
        this.port = port
    }

    /**
     * Configures an additional option to apply to the Netty channel.
     */
    fun <T : Any> nettyChannelOption(key: ChannelOption<T>, value: T) {
        nettyChannelOptions[key] = value
    }

    /**
     * Configures additional options to apply to the Netty channel.
     */
    fun nettyChannelOptions(vararg options: Pair<ChannelOption<*>, Any>) {
        nettyChannelOptions.putAll(options)
    }
}
