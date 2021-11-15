package io.qalipsis.plugins.netty.http.spec

import io.netty.handler.codec.http.HttpVersion

/**
 * @author Eric Jessé
 */
enum class HttpVersion {

    HTTP_1_1 {
        override val nettyVersion = HttpVersion.HTTP_1_1
        override val protocol = "HTTP/1.1"
    },
    HTTP_2_0 {
        override val nettyVersion = HttpVersion.HTTP_1_1
        override val protocol = "HTTP/2.0"
    };

    internal abstract val nettyVersion: HttpVersion

    internal abstract val protocol: String
}
