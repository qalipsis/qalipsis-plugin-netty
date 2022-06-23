package io.qalipsis.plugins.netty.http

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.mockk.mockk
import io.netty.handler.codec.http.HttpMethod
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.plugins.netty.http.request.HttpRequest
import io.qalipsis.plugins.netty.http.request.SimpleHttpRequest
import io.qalipsis.plugins.netty.http.response.HttpBodyDeserializer
import io.qalipsis.plugins.netty.http.response.ResponseConverter
import io.qalipsis.plugins.netty.http.spec.QueryHttpClientStepSpecification
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.coroutines.CoroutineContext

@WithMockk
@Suppress("UNCHECKED_CAST")
internal class QueryHttpClientStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<QueryHttpClientStepSpecificationConverter>() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var ioCoroutineContext: CoroutineContext

    private val bodyDeserializer1: HttpBodyDeserializer = mockk {
        every { order } returns -1
    }

    private val bodyDeserializer2: HttpBodyDeserializer = mockk {
        every { order } returns 1
    }

    @SpyK
    // List of serializers in the reverse order.
    private var deserializers = listOf(bodyDeserializer2, bodyDeserializer1)

    @RelaxedMockK
    lateinit var connectionOwner: HttpClientStep<Long, *>

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<QueryHttpClientStepSpecification<*, *>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name and retry policy to step when connection owner exists on the DAG`() =
        testDispatcherProvider.runTest {
            // given
            val requestSpecification: suspend (ctx: StepContext<*, *>, input: String) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head") }
            val spec = QueryHttpClientStepSpecification<String, String>("my-previous-http-step")
            spec.apply {
                name = "my-step"
                retryPolicy = mockedRetryPolicy
                request(requestSpecification)
                monitoring {
                    events = true
                }
            }
            val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
            coEvery {
                directedAcyclicGraph.findStep("my-previous-http-step")
            } returns (connectionOwner to relaxedMockk())

            // when
            converter.convert<String, Int>(
                creationContext as StepCreationContext<QueryHttpClientStepSpecification<*, *>>
            )

            // then
            creationContext.createdStep!!.let {
                assertThat(it).all {
                    isInstanceOf(QueryHttpClientStep::class)
                    prop("name").isEqualTo("my-step")
                    prop("retryPolicy").isSameAs(mockedRetryPolicy)
                    prop("ioCoroutineContext").isSameAs(ioCoroutineContext)
                    prop("requestFactory").isSameAs(requestSpecification)
                    prop("connectionOwner").isSameAs(connectionOwner)
                    typedProp<ResponseConverter<*>>("responseConverter").all {
                        prop("argumentType").isSameAs(String::class)
                        typedProp<List<HttpBodyDeserializer>>("deserializers").containsOnly(
                            bodyDeserializer1,
                            bodyDeserializer2
                        )
                    }
                    prop("eventsLogger").isSameAs(eventsLogger)
                    prop("meterRegistry").isNull()
                }
            }
        }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner exists on the DAG`() =
        testDispatcherProvider.runTest {
            // given
            val requestSpecification: suspend (ctx: StepContext<*, *>, input: String) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head") }
            val spec = QueryHttpClientStepSpecification<String, String>("my-previous-http-step")
            spec.apply {
                request(requestSpecification)
                monitoring {
                    meters = true
                }
            }.deserialize(Entity::class)
            val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
            coEvery {
                directedAcyclicGraph.findStep("my-previous-http-step")
            } returns (connectionOwner to relaxedMockk())

            // when
            converter.convert<String, Int>(
                creationContext as StepCreationContext<QueryHttpClientStepSpecification<*, *>>
            )

            // then
            creationContext.createdStep!!.let {
                assertThat(it).all {
                    isInstanceOf(QueryHttpClientStep::class)
                    prop("name").isNotNull()
                    prop("retryPolicy").isNull()
                    prop("ioCoroutineContext").isSameAs(ioCoroutineContext)
                    prop("requestFactory").isSameAs(requestSpecification)
                    prop("connectionOwner").isSameAs(connectionOwner)
                    typedProp<ResponseConverter<*>>("responseConverter").all {
                        prop("argumentType").isSameAs(Entity::class)
                        typedProp<List<HttpBodyDeserializer>>("deserializers").containsOnly(
                            bodyDeserializer1,
                            bodyDeserializer2
                        )
                    }
                    prop("eventsLogger").isNull()
                    prop("meterRegistry").isSameAs(meterRegistry)
                }
            }

            verifyOnce { connectionOwner.addUsage() }
        }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner is indirectly referenced`() =
        testDispatcherProvider.runTest {
            // given
            val requestSpecification: suspend (ctx: StepContext<*, *>, input: String) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head") }
            val spec = QueryHttpClientStepSpecification<String, String>("my-previous-kept-alive-http-step")
            spec.apply {
                request(requestSpecification)
                monitoring {
                    events = true
                    meters = true
                }
            }.deserialize(Entity::class)
            val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
            val previousKeptAliveHttpClientStep = relaxedMockk<QueryHttpClientStep<String, String>>()
            every { previousKeptAliveHttpClientStep.connectionOwner } returns connectionOwner
            coEvery {
                directedAcyclicGraph.findStep("my-previous-kept-alive-http-step")
            } returns (previousKeptAliveHttpClientStep to relaxedMockk())

            // when
            converter.convert<String, Int>(
                creationContext as StepCreationContext<QueryHttpClientStepSpecification<*, *>>
            )

            // then
            creationContext.createdStep!!.let {
                assertThat(it).all {
                    isInstanceOf(QueryHttpClientStep::class)
                    prop("name").isNotNull()
                    prop("retryPolicy").isNull()
                    prop("ioCoroutineContext").isSameAs(ioCoroutineContext)
                    prop("requestFactory").isSameAs(requestSpecification)
                    prop("connectionOwner").isSameAs(connectionOwner)
                    typedProp<ResponseConverter<*>>("responseConverter").all {
                        prop("argumentType").isSameAs(Entity::class)
                        typedProp<List<HttpBodyDeserializer>>("deserializers").containsOnly(
                            bodyDeserializer1,
                            bodyDeserializer2
                        )
                    }
                    prop("eventsLogger").isSameAs(eventsLogger)
                    prop("meterRegistry").isSameAs(meterRegistry)
                }
            }

            verifyOnce { connectionOwner.addUsage() }
        }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner does not exist on the DAG`() =
        testDispatcherProvider.runTest {
            // given
            val requestSpecification: suspend (ctx: StepContext<*, *>, input: String) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head") }
            val spec = QueryHttpClientStepSpecification<String, String>("my-previous-http-step")
            spec.apply {
                request(requestSpecification)
            }
            val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
            coEvery { directedAcyclicGraph.findStep(any()) } returns null

            // when
            org.junit.jupiter.api.assertThrows<InvalidSpecificationException> {
                converter.convert<String, Int>(
                    creationContext as StepCreationContext<QueryHttpClientStepSpecification<*, *>>
                )
            }
        }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner exists but is of a different type`() =
        testDispatcherProvider.runTest {
            // given
            val requestSpecification: suspend (ctx: StepContext<*, *>, input: String) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head") }
            val spec = QueryHttpClientStepSpecification<String, String>("my-previous-http-step")
            spec.apply {
                request(requestSpecification)
            }
            val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
            coEvery { directedAcyclicGraph.findStep(any()) } returns (relaxedMockk<Step<*, *>>() to relaxedMockk())

            // when
            org.junit.jupiter.api.assertThrows<InvalidSpecificationException> {
                converter.convert<String, Int>(
                    creationContext as StepCreationContext<QueryHttpClientStepSpecification<*, *>>
                )
            }
        }

    private data class Entity(val field: String)
}

