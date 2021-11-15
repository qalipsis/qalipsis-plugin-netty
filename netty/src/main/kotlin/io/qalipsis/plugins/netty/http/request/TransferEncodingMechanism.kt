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
