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