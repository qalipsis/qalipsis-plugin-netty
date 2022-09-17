/*
 * Copyright 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.plugins.netty.http

import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepDecorator
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.plugins.netty.http.spec.CloseHttpClientStepSpecification
import jakarta.inject.Named
import kotlin.coroutines.CoroutineContext

/**
 * [StepSpecificationConverter] from [CloseHttpClientStepSpecification] to [CloseHttpClientStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class CloseHttpClientStepSpecificationConverter(
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineContext: CoroutineContext
) :
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

        val step = CloseHttpClientStep<I>(spec.name, ioCoroutineContext, connectionOwner)
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
