package io.qalipsis.plugins.netty.http.response

import kotlin.reflect.KClass

interface HttpBodyDeserializer {

    /**
     * Verifies if the deserializer is relevant of the provided [MediaType].
     */
    fun accept(mediaType: MediaType): Boolean

    /**
     * Converts the payload of a response with a type in [mediaTypes] into an instance of [B].
     */
    fun <B : Any> convert(content: ByteArray, mediaType: MediaType, type: KClass<B>): B?

    /**
     * Order of the deserializer for the lookup. The lowest the value, the earliest in the lookup.
     */
    val order: Int
}
