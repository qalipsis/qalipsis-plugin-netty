import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.allopen")
    `java-test-fixtures`
}

description = "Qalipsis Plugins - Netty clients"

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }
}

tasks.withType<Test> {
    // Enables the search of memory leaks in the Netty buffers when running all tests.
    systemProperties("io.netty.leakDetectionLevel" to "paranoid")
    jvmArgs("-XX:-MaxFDLimit")
}

allOpen {
    annotations(
        "io.micronaut.aop.Around",
        "jakarta.inject.Singleton",
        "io.qalipsis.api.annotations.StepConverter",
        "io.qalipsis.api.annotations.StepDecorator",
        "io.qalipsis.api.annotations.PluginComponent",
        "io.qalipsis.api.annotations.Spec",
        "io.micronaut.validation.Validated"
    )
}

val nettyVersion = "4.1.69.Final"
val micronautVersion: String by project
val jacksonVersion: String by project
val kotlinCoroutinesVersion: String by project
val guavaVersion: String by project
val testContainersVersion: String by project
val catadioptreVersion: String by project

kotlin.sourceSets["test"].kotlin.srcDir("build/generated/source/kaptKotlin/catadioptre")
kapt.useBuildCache = false

logger.lifecycle("Using Micronaut $micronautVersion and Netty $nettyVersion")

dependencies {
    compileOnly("io.aeris-consulting:catadioptre-annotations:${catadioptreVersion}")
    compileOnly(kotlin("stdlib"))
    compileOnly(platform("io.micronaut:micronaut-bom:$micronautVersion"))
    compileOnly("io.micronaut:micronaut-runtime")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:${kotlinCoroutinesVersion}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation(platform("io.netty:netty-bom:$nettyVersion"))
    api("io.netty:netty-handler")
    api("io.netty:netty-handler-proxy")
    api("io.netty:netty-transport")
    implementation(group = "io.netty", name = "netty-transport-native-epoll", classifier = "linux-x86_64")
    implementation(group = "io.netty", name = "netty-transport-native-kqueue", classifier = "osx-x86_64")
    api("io.netty:netty-buffer")
    api("io.netty:netty-codec")
    api("io.netty:netty-codec-http")
    api("io.netty:netty-codec-http2")
    api("io.netty:netty-codec-mqtt")
    implementation("com.github.ben-manes.caffeine:caffeine:2.8.1")
    implementation("com.google.guava:guava:$guavaVersion")

    api("io.qalipsis:api-common:${project.version}")
    api("io.qalipsis:api-dsl:${project.version}")

    kapt(platform("io.micronaut:micronaut-bom:$micronautVersion"))
    kapt("io.qalipsis:api-processors:${project.version}")
    kapt("io.qalipsis:api-dsl:${project.version}")
    kapt("io.qalipsis:api-common:${project.version}")
    kapt("io.aeris-consulting:catadioptre-annotations:${catadioptreVersion}")

    testFixturesImplementation(kotlin("stdlib"))
    testFixturesImplementation("io.qalipsis:api-common:${project.version}")
    testFixturesImplementation("io.qalipsis:test:${project.version}")
    testFixturesImplementation(platform("io.netty:netty-bom:$nettyVersion"))
    testFixturesImplementation("io.netty:netty-handler")
    testFixturesImplementation("io.netty:netty-transport")
    testFixturesImplementation("io.netty:netty-handler-proxy")
    testFixturesImplementation("io.netty:netty-buffer")
    testFixturesImplementation("io.netty:netty-example") {  // FIXME Remove after implementation
        exclude("io.netty", "netty-tcnative")
    }

    testFixturesImplementation(platform("io.micronaut:micronaut-bom:$micronautVersion"))
    testFixturesImplementation("io.micronaut:micronaut-http-server-netty")
    testFixturesImplementation("io.micronaut.reactor:micronaut-reactor")
    testFixturesImplementation("io.aeris-consulting:catadioptre-kotlin:+")
    kaptTestFixtures("io.micronaut:micronaut-inject-java")

    testImplementation("io.qalipsis:test:${project.version}")
    testImplementation("io.qalipsis:api-dsl:${project.version}")
    testImplementation(testFixtures("io.qalipsis:api-dsl:${project.version}"))
    testImplementation(testFixtures("io.qalipsis:api-common:${project.version}"))
    testImplementation(testFixtures("io.qalipsis:runtime:${project.version}"))
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("io.micronaut:micronaut-http-client") // FIXME Remove after implementation
    testImplementation("org.apache.commons:commons-lang3:3.12.0")
    testImplementation("io.aeris-consulting:catadioptre-kotlin:${catadioptreVersion}")
    testRuntimeOnly("io.qalipsis:runtime:${project.version}")

    kaptTest(platform("io.micronaut:micronaut-bom:$micronautVersion"))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest("io.qalipsis:api-processors:${project.version}")
}
