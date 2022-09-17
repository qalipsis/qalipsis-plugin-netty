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

package io.qalipsis.plugins.netty

import io.netty.channel.EventLoopGroup

/**
 * Special service in charge of providing the pre-configured [EventLoopGroup].
 * For now, only a default unconfigurable instance is supported.
 *
 * @author Eric Jessé
 */
interface EventLoopGroupSupplier {

    /**
     * Provides the default instance of [EventLoopGroup]
     */
    fun getGroup(): EventLoopGroup

}