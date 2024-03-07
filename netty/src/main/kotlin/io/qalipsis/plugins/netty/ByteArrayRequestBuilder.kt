/*
 * Copyright 2024 AERIS IT Solutions GmbH
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

import java.util.Base64

/**
 * Builder for TCP and UDP requests.
 */
interface ByteArrayRequestBuilder : RequestBuilder<ByteArray> {

    /**
     * Uses the [byteArray] as input for to send as payload.
     */
    fun bytes(byteArray: ByteArray) = byteArray

    /**
     * Uses the [input] that should be a valid Base64 string to send as payload.
     */
    fun base64(input: String): ByteArray

    /**
     * Uses the [input] that should be a valid Base64 byte array to send as payload.
     */
    fun base64(input: ByteArray): ByteArray

    /**
     * Uses the [input] that should be a valid hex string to send as payload.
     */
    fun hex(input: String): ByteArray {
        check(input.length % 2 == 0) { "The hex input should have an even length" }
        return ByteArray(input.length / 2) { index ->
            Integer.parseInt(input, index * 2, 2 * (index + 1), 16).toByte()
        }
    }

}

internal object ByteArrayRequestBuilderImpl : ByteArrayRequestBuilder {

    private val base64Decoder = Base64.getDecoder()


    /**
     * Uses the [input] that should be a valid Base64 string to send as payload.
     */
    override fun base64(input: String) = base64Decoder.decode(input)

    /**
     * Uses the [input] that should be a valid Base64 byte array to send as payload.
     */
    override fun base64(input: ByteArray) = base64Decoder.decode(input)

}