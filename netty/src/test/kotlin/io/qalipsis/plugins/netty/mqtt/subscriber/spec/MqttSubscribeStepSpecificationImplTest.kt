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

package io.qalipsis.plugins.netty.mqtt.subscriber.spec

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.qalipsis.api.messaging.deserializer.MessageJsonDeserializer
import io.qalipsis.api.messaging.deserializer.MessageStringDeserializer
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.netty.mqtt.spec.MqttAuthentication
import io.qalipsis.plugins.netty.mqtt.spec.MqttConnectionConfiguration
import io.qalipsis.plugins.netty.mqtt.spec.MqttQoS
import io.qalipsis.plugins.netty.mqtt.spec.MqttVersion
import io.qalipsis.plugins.netty.mqtt.subscriber.deserializer.MqttByteArrayDeserializer
import io.qalipsis.plugins.netty.netty
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Gabriel Moraes
 */
internal class MqttSubscribeStepSpecificationImplTest {
    @Test
    internal fun `should add minimal specification to the scenario with default values`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.netty().mqttSubscribe {
            name = "my-step"
            topicFilter("test")
            clientName("")
        }

        assertThat(scenario.rootSteps.first()).isInstanceOf(MqttSubscribeStepSpecificationImpl::class).all {
            prop(MqttSubscribeStepSpecificationImpl<*>::name).isEqualTo("my-step")
            prop(MqttSubscribeStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::meters).isFalse()
                prop(StepMonitoringConfiguration::events).isFalse()
            }
            prop(MqttSubscribeStepSpecificationImpl<*>::mqttSubscribeConfiguration).all {
                prop(MqttSubscribeConfiguration<*>::concurrency).isEqualTo(2)
                prop(MqttSubscribeConfiguration<*>::topic).isEqualTo("test")
                prop(MqttSubscribeConfiguration<*>::subscribeQoS).isEqualTo(MqttQoS.AT_LEAST_ONCE)
                prop(MqttSubscribeConfiguration<*>::protocol).isEqualTo(MqttVersion.MQTT_3_1_1)
                prop(MqttSubscribeConfiguration<*>::client).isEqualTo("")
                prop(MqttSubscribeConfiguration<*>::valueDeserializer).isNotNull().isInstanceOf(
                    MqttByteArrayDeserializer::class
                )
                prop(MqttSubscribeConfiguration<*>::connectionConfiguration).all {
                    prop(MqttConnectionConfiguration::host).isEqualTo("localhost")
                    prop(MqttConnectionConfiguration::port).isEqualTo(1883)
                    prop(MqttConnectionConfiguration::reconnect).isEqualTo(true)
                }
                prop(MqttSubscribeConfiguration<*>::authentication).all {
                    prop(MqttAuthentication::username).isEqualTo("")
                    prop(MqttAuthentication::password).isEqualTo("")
                }
            }
            prop(MqttSubscribeStepSpecificationImpl<*>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
            }
        }
    }

    @Test
    internal fun `should add a complete specification to the scenario as broadcast with monitoring`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.netty().mqttSubscribe {
            name = "my-complete-step"
            topicFilter("complete-test")
            concurrency(10)
            qoS(MqttQoS.AT_MOST_ONCE)
            monitoring {
                meters = true
                events = false
            }
            connect {
                host = "anotherhost"
                port = 8893
            }
            auth {
                password = "test"
                username = "test"
            }
            protocol(MqttVersion.MQTT_5)
            clientName("name")
            unicast(6, Duration.ofDays(1))
        }.deserialize(MessageStringDeserializer::class)

        assertThat(scenario.rootSteps.first()).isInstanceOf(MqttSubscribeStepSpecificationImpl::class).all {
            prop(MqttSubscribeStepSpecificationImpl<*>::name).isEqualTo("my-complete-step")
            prop(MqttSubscribeStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::meters).isTrue()
                prop(StepMonitoringConfiguration::events).isFalse()
            }
            prop(MqttSubscribeStepSpecificationImpl<*>::mqttSubscribeConfiguration).all {
                prop(MqttSubscribeConfiguration<*>::concurrency).isEqualTo(10)
                prop(MqttSubscribeConfiguration<*>::topic).isEqualTo("complete-test")
                prop(MqttSubscribeConfiguration<*>::subscribeQoS).isEqualTo(MqttQoS.AT_MOST_ONCE)
                prop(MqttSubscribeConfiguration<*>::protocol).isEqualTo(MqttVersion.MQTT_5)
                prop(MqttSubscribeConfiguration<*>::client).isEqualTo("name")
                prop(MqttSubscribeConfiguration<*>::valueDeserializer).isNotNull()
                    .isInstanceOf(MessageStringDeserializer::class)
                prop(MqttSubscribeConfiguration<*>::connectionConfiguration).all {
                    prop(MqttConnectionConfiguration::host).isEqualTo("anotherhost")
                    prop(MqttConnectionConfiguration::port).isEqualTo(8893)
                    prop(MqttConnectionConfiguration::reconnect).isEqualTo(true)
                }
                prop(MqttSubscribeConfiguration<*>::authentication).all {
                    prop(MqttAuthentication::username).isEqualTo("test")
                    prop(MqttAuthentication::password).isEqualTo("test")
                }
            }
            prop(MqttSubscribeStepSpecificationImpl<*>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(6)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ofDays(1))
            }
        }
    }

    @Test
    internal fun `should add a complete specification to the scenario as broadcast with eventsLogger`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.netty().mqttSubscribe {
            name = "my-complete-step"
            topicFilter("complete-test")
            concurrency(10)
            qoS(MqttQoS.AT_MOST_ONCE)
            monitoring {
                meters = false
                events = true
            }
            connect {
                host = "anotherhost"
                port = 8893
            }
            auth {
                password = "test"
                username = "test"
            }
            protocol(MqttVersion.MQTT_5)
            clientName("name")
            unicast(6, Duration.ofDays(1))
        }.deserialize(MessageStringDeserializer::class)

        assertThat(scenario.rootSteps.first()).isInstanceOf(MqttSubscribeStepSpecificationImpl::class).all {
            prop(MqttSubscribeStepSpecificationImpl<*>::name).isEqualTo("my-complete-step")
            prop(MqttSubscribeStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::meters).isFalse()
                prop(StepMonitoringConfiguration::events).isTrue()
            }
            prop(MqttSubscribeStepSpecificationImpl<*>::mqttSubscribeConfiguration).all {
                prop(MqttSubscribeConfiguration<*>::concurrency).isEqualTo(10)
                prop(MqttSubscribeConfiguration<*>::topic).isEqualTo("complete-test")
                prop(MqttSubscribeConfiguration<*>::subscribeQoS).isEqualTo(MqttQoS.AT_MOST_ONCE)
                prop(MqttSubscribeConfiguration<*>::protocol).isEqualTo(MqttVersion.MQTT_5)
                prop(MqttSubscribeConfiguration<*>::client).isEqualTo("name")
                prop(MqttSubscribeConfiguration<*>::valueDeserializer).isNotNull()
                    .isInstanceOf(MessageStringDeserializer::class)
                prop(MqttSubscribeConfiguration<*>::connectionConfiguration).all {
                    prop(MqttConnectionConfiguration::host).isEqualTo("anotherhost")
                    prop(MqttConnectionConfiguration::port).isEqualTo(8893)
                    prop(MqttConnectionConfiguration::reconnect).isEqualTo(true)
                }
                prop(MqttSubscribeConfiguration<*>::authentication).all {
                    prop(MqttAuthentication::username).isEqualTo("test")
                    prop(MqttAuthentication::password).isEqualTo("test")
                }
            }
            prop(MqttSubscribeStepSpecificationImpl<*>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(6)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ofDays(1))
            }
        }
    }

    @Test
    internal fun `should keep default values and use another deserialization`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.netty().mqttSubscribe {
            name = "my-step"
            topicFilter("test")
        }.deserialize(MessageJsonDeserializer(String::class))

        assertThat(scenario.rootSteps.first()).isInstanceOf(MqttSubscribeStepSpecificationImpl::class).all {
            prop(MqttSubscribeStepSpecificationImpl<*>::name).isEqualTo("my-step")
            prop(MqttSubscribeStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::meters).isFalse()
                prop(StepMonitoringConfiguration::events).isFalse()
            }
            prop(MqttSubscribeStepSpecificationImpl<*>::mqttSubscribeConfiguration).all {
                prop(MqttSubscribeConfiguration<*>::concurrency).isEqualTo(2)
                prop(MqttSubscribeConfiguration<*>::topic).isEqualTo("test")
                prop(MqttSubscribeConfiguration<*>::subscribeQoS).isEqualTo(MqttQoS.AT_LEAST_ONCE)
                prop(MqttSubscribeConfiguration<*>::protocol).isEqualTo(MqttVersion.MQTT_3_1_1)
                prop(MqttSubscribeConfiguration<*>::client).isEqualTo("")
                prop(MqttSubscribeConfiguration<*>::valueDeserializer).isNotNull()
                    .isInstanceOf(MessageJsonDeserializer::class)
                prop(MqttSubscribeConfiguration<*>::connectionConfiguration).all {
                    prop(MqttConnectionConfiguration::host).isEqualTo("localhost")
                    prop(MqttConnectionConfiguration::port).isEqualTo(1883)
                    prop(MqttConnectionConfiguration::reconnect).isEqualTo(true)
                }
                prop(MqttSubscribeConfiguration<*>::authentication).all {
                    prop(MqttAuthentication::username).isEqualTo("")
                    prop(MqttAuthentication::password).isEqualTo("")
                }
            }
            prop(MqttSubscribeStepSpecificationImpl<*>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
            }
        }
    }
}