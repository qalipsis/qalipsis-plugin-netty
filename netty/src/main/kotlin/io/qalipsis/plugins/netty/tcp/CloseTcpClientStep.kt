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

package io.qalipsis.plugins.netty.tcp

import io.qalipsis.api.context.StepName
import io.qalipsis.api.steps.ErrorProcessingStep
import io.qalipsis.plugins.netty.socket.CloseSocketClientStep

/**
 * Step to close a TCP connection that was created in an earlier step and kept open.
 *
 * @author Eric Jessé
 */
internal class CloseTcpClientStep<I>(
    id: StepName,
    connectionOwner: TcpClientStep<*, *>,
) : CloseSocketClientStep<I>(id, connectionOwner), ErrorProcessingStep<I, I>
