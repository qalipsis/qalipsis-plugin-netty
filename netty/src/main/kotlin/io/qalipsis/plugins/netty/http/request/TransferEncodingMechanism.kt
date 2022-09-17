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

package io.qalipsis.plugins.netty.http.request

/**
 * Allowed mechanism for multipart mechanism.
 *
 * @author Eric Jessé
 */
enum class TransferEncodingMechanism(internal val value: String) {

    /**
     * Default encoding.
     */
    BIT7("7bit"),

    /**
     * Short lines but not in ASCII - no encoding.
     */
    BIT8("8bit"),

    /**
     * Could be long text not in ASCII - no encoding.
     */
    BINARY("binary")

}
