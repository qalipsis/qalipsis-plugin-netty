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

package io.qalipsis.plugins.netty.http

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepDecorator
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.plugins.netty.http.spec.CloseHttpClientStepSpecification

/**
 * [StepSpecificationConverter] from [CloseHttpClientStepSpecification] to [CloseHttpClientStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class CloseHttpClientStepSpecificationConverter :
    StepSpecificationConverter<CloseHttpClientStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return (stepSpecification is CloseHttpClientStepSpecification)
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<CloseHttpClientStepSpecification<*>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as CloseHttpClientStepSpecification<I>
        // Validates that the referenced step exists and is a HTTP step.
        val referencedStep = creationContext.directedAcyclicGraph.findStep(spec.stepName)?.first
        val connectionOwner = findHttpClientStep(referencedStep) ?: throw InvalidSpecificationException(
            "Step with specified name ${spec.stepName} does not exist or is not a HTTP step: $referencedStep"
        )

        connectionOwner.keepOpen()

        val step = CloseHttpClientStep<I>(spec.name, connectionOwner)
        creationContext.createdStep(step)
    }

    /**
     * Searches the referenced [SimpleHttpClientStep] if any.
     */
    private fun findHttpClientStep(foundStep: Step<*, *>?): HttpClientStep<*, *>? {
        return when (foundStep) {
            is QueryHttpClientStep<*, *> -> {
                // We can chain the names from step to step.
                foundStep.connectionOwner
            }

            is HttpClientStep<*, *> -> foundStep
            is StepDecorator<*, *> -> findHttpClientStep(foundStep.decorated)
            else -> null
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
