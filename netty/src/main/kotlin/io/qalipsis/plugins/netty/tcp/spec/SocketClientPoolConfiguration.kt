package io.qalipsis.plugins.netty.tcp.spec

import io.qalipsis.api.annotations.Spec
import javax.validation.constraints.Min

/**
 * @property size constant size of the pool of connection
 * @property checkHealthBeforeUse set to [true] to enable the health check when acquiring a connection
 */
@Spec
class SocketClientPoolConfiguration internal constructor(
    @field:Min(1) var size: Int = 1,
    var checkHealthBeforeUse: Boolean = false
)
