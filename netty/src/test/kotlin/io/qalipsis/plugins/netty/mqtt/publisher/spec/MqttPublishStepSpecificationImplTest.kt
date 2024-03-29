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

package io.qalipsis.plugins.netty.mqtt.publisher.spec

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.aerisconsulting.catadioptre.getProperty
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.netty.mqtt.publisher.MqttPublishRecord
import io.qalipsis.plugins.netty.mqtt.spec.MqttAuthentication
import io.qalipsis.plugins.netty.mqtt.spec.MqttConnectionConfiguration
import io.qalipsis.plugins.netty.mqtt.spec.MqttVersion
import io.qalipsis.plugins.netty.netty
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Gabriel Moraes
 */
internal class MqttPublishStepSpecificationImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    fun `should add minimal configuration for the step`() = testDispatcherProvider.runTest {
        val previousStep = DummyStepSpecification()
        previousStep.netty().mqttPublish {
            name = "publish-step"
            connect {
                host = "localhost"
                port = 1889
            }
            clientName("test")
            protocol(MqttVersion.MQTT_3_1)
            records { _, _ ->
                listOf(MqttPublishRecord("records", "topic"))
            }
        }

        val nextStep = previousStep.nextSteps[0]
        assertThat(nextStep).isInstanceOf(MqttPublishStepSpecificationImpl::class).all {
            prop(MqttPublishStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
            prop(MqttPublishStepSpecificationImpl<*>::mqttPublishConfiguration).all {
                prop(MqttPublishConfiguration<*>::authentication).all{
                    prop(MqttAuthentication::password).isEqualTo("")
                    prop(MqttAuthentication::username).isEqualTo("")
                }
                prop(MqttPublishConfiguration<*>::client).isEqualTo("test")
                prop(MqttPublishConfiguration<*>::protocol).isEqualTo(MqttVersion.MQTT_3_1)
                prop(MqttPublishConfiguration<*>::connectionConfiguration).all{
                    prop(MqttConnectionConfiguration::host).isEqualTo("localhost")
                    prop(MqttConnectionConfiguration::port).isEqualTo(1889)
                    prop(MqttConnectionConfiguration::reconnect).isTrue()
                }
                prop(MqttPublishConfiguration<*>::recordsFactory).isNotNull()
            }
        }

        val recordsFactory = nextStep.getProperty<MqttPublishConfiguration<*>>("mqttPublishConfiguration").getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> List<MqttPublishRecord>>("recordsFactory")
        assertThat(recordsFactory(relaxedMockk(), relaxedMockk())).isEqualTo(listOf(MqttPublishRecord("records", "topic")))
    }

    @Test
    fun `should add a complete configuration for the step with monitoring`() = testDispatcherProvider.runTest {
        val previousStep = DummyStepSpecification()
        previousStep.netty().mqttPublish {
            name = "publish-step"
            connect {
                host = "localhost"
                port = 1889
                reconnect = false
            }
            clientName("test")
            protocol(MqttVersion.MQTT_3_1)
            monitoring {
                meters = true
                events = false
            }
            auth {
                password = "test"
                username = "test"
            }
            records { _, _ ->
                listOf(MqttPublishRecord("records", "topic"))
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(MqttPublishStepSpecificationImpl::class).all {
            prop(MqttPublishStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
            prop(MqttPublishStepSpecificationImpl<*>::mqttPublishConfiguration).all {
                prop(MqttPublishConfiguration<*>::authentication).all{
                    prop(MqttAuthentication::password).isEqualTo("test")
                    prop(MqttAuthentication::username).isEqualTo("test")
                }
                prop(MqttPublishConfiguration<*>::client).isEqualTo("test")
                prop(MqttPublishConfiguration<*>::protocol).isEqualTo(MqttVersion.MQTT_3_1)
                prop(MqttPublishConfiguration<*>::connectionConfiguration).all{
                    prop(MqttConnectionConfiguration::host).isEqualTo("localhost")
                    prop(MqttConnectionConfiguration::port).isEqualTo(1889)
                    prop(MqttConnectionConfiguration::reconnect).isFalse()
                }
                prop(MqttPublishConfiguration<*>::recordsFactory).isNotNull()
            }
        }

        val recordsFactory =
            previousStep.nextSteps[0]
                .getProperty<MqttPublishConfiguration<*>>("mqttPublishConfiguration")
                .getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> List<Any>>("recordsFactory")
        assertThat(recordsFactory(relaxedMockk(), relaxedMockk())).hasSize(1)
    }

    @Test
    fun `should add a complete configuration for the step with eventsLogger`() = testDispatcherProvider.runTest {
        val previousStep = DummyStepSpecification()
        previousStep.netty().mqttPublish {
            name = "publish-step"
            connect {
                host = "localhost"
                port = 1889
                reconnect = false
            }
            clientName("test")
            protocol(MqttVersion.MQTT_3_1)
            monitoring {
                meters = false
                events = true
            }
            auth {
                password = "test"
                username = "test"
            }
            records { _, _ ->
                listOf(MqttPublishRecord("records", "topic"))
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(MqttPublishStepSpecificationImpl::class).all {
            prop(MqttPublishStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
            prop(MqttPublishStepSpecificationImpl<*>::mqttPublishConfiguration).all {
                prop(MqttPublishConfiguration<*>::authentication).all{
                    prop(MqttAuthentication::password).isEqualTo("test")
                    prop(MqttAuthentication::username).isEqualTo("test")
                }
                prop(MqttPublishConfiguration<*>::client).isEqualTo("test")
                prop(MqttPublishConfiguration<*>::protocol).isEqualTo(MqttVersion.MQTT_3_1)
                prop(MqttPublishConfiguration<*>::connectionConfiguration).all{
                    prop(MqttConnectionConfiguration::host).isEqualTo("localhost")
                    prop(MqttConnectionConfiguration::port).isEqualTo(1889)
                    prop(MqttConnectionConfiguration::reconnect).isFalse()
                }
                prop(MqttPublishConfiguration<*>::recordsFactory).isNotNull()
            }
        }

        val recordsFactory =
            previousStep.nextSteps[0]
                .getProperty<MqttPublishConfiguration<*>>("mqttPublishConfiguration")
                .getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> List<Any>>("recordsFactory")
        assertThat(recordsFactory(relaxedMockk(), relaxedMockk())).hasSize(1)
    }
}
