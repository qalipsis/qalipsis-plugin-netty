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
