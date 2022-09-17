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

package io.qalipsis.plugins.netty

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Interface of a test server usable as a JUnit5 extension.
 *
 * @author Eric Jessé
 */
interface Server : BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    val port: Int

    fun start()

    fun stop()

    override fun beforeAll(context: ExtensionContext) {
        // If the test suite has nested classes, the action is only performed on the upper class.
        if (context.requiredTestClass.enclosingClass == null) {
            this.start()
        }
    }

    override fun afterAll(context: ExtensionContext) {
        // If the test suite has nested classes, the action is only performed on the upper class.
        if (context.requiredTestClass.enclosingClass == null) {
            this.stop()
        }
    }

    override fun beforeEach(context: ExtensionContext) = Unit

    override fun afterEach(context: ExtensionContext) = Unit
}