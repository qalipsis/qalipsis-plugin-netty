package io.qalipsis.plugins.netty.http.client

/**
 * When multiplexing is allowed, this generates the next value. Implementations should be thread-safe.
 */
internal interface StreamIdGenerator<T> {

    /**
     * Generates the next value, or a [IllegalStateException] if it is no longer possible, in which case the client has to be closed.
     */
    @Throws(IllegalStateException::class)
    fun next(): T
}