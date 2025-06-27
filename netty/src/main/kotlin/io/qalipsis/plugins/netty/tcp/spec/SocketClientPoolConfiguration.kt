/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

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
