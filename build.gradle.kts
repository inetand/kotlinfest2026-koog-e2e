plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "harness"

// Spring Boot's BOM pins older kotlinx versions than Koog needs; these override the BOM properties.
extra["kotlin.version"] = "2.3.21"
extra["kotlin-coroutines.version"] = "1.11.0"
extra["kotlin-serialization.version"] = "1.11.0"

kotlin { jvmToolchain(21) }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter") // DI and typed configuration
    implementation("ai.koog:koog-agents:1.1.1") // typed strategy graphs and subgraphs
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core") // Channel, channelFlow, Semaphore
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json") // scenarios, run records, result lines
    implementation("info.picocli:picocli-spring-boot-starter:4.7.7") // subcommands and generated help
    implementation("io.github.oshai:kotlin-logging-jvm:8.0.01") // Koog's logger; declared only to silence its stdout banner
    implementation("org.yaml:snakeyaml") // the Maestro flow the driver writes
    implementation(kotlin("reflect"))

    testImplementation("ai.koog:agents-test:1.1.1") // Koog's graph-testing feature
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed") }
}

tasks.bootJar { archiveFileName.set("harness.jar") }

tasks.jar { enabled = false }
