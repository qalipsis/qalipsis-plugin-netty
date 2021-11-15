package io.qalipsis.plugins.netty.proxy.server

import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/**
 * Docker container for a HTTP proxy.
 *
 * @author Eric Jessé
 */
class HttpProxyContainer : GenericContainer<HttpProxyContainer>(DockerImageName.parse(IMAGE_NAME)) {

    init {
        withExposedPorts(PORT)
    }

    val port: Int
        get() = getMappedPort(PORT)

    companion object {

        private const val IMAGE_NAME = "datadog/squid"

        private const val PORT = 3128

    }
}
