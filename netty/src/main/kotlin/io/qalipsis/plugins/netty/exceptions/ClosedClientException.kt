package io.qalipsis.plugins.netty.exceptions

import io.qalipsis.api.context.MinionId

class ClosedClientException(minionId: MinionId) :
    RuntimeException("The client for minion $minionId is already closed")
