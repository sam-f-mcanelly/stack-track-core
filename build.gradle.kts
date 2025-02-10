plugins {
    kotlin("jvm") version "2.0.20"
    application
    id("org.jetbrains.kotlin.kapt") version "2.0.20"
    // kotlin("plugin.serialization") version "1.8.10"
    id("io.ktor.plugin") version "3.0.3"
}

group = "com.bitcointracker"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.ktor:ktor-server-netty:2.3.1")
    implementation("io.ktor:ktor-server-core:2.3.1")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.1")
    implementation("io.ktor:ktor-server-cors")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.google.code.gson:gson:2.8.8")
    implementation("com.google.dagger:dagger:2.48")
    implementation("io.ktor:ktor-serialization-jackson:2.3.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    kapt("com.google.dagger:dagger-compiler:2.48")
    implementation("javax.inject:javax.inject:1")
    implementation("org.slf4j:slf4j-api:2.0.9")

    // H2 database
    implementation("com.h2database:h2:2.2.224")

    // Exposed ORM
    implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
    implementation("org.jetbrains.exposed:exposed-core:0.41.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")
    implementation("org.jetbrains.exposed:exposed-java-time:0.41.1")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.41.1")

    // co-routines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")

    // Testing dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("io.ktor:ktor-server-test-host:2.3.7")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.20")
    testImplementation("io.mockk:mockk:1.13.8")
}

application {
    mainClass.set("com.bitcointracker.ApplicationKt")
}

ktor {
    docker {
        localImageName.set("bitcoin-tax-core")
        imageTag.set("0.0.1-preview")
        jreVersion.set(JavaVersion.VERSION_22)
        portMappings.set(listOf(
            io.ktor.plugin.features.DockerPortMapping(
                90,
                9090,
                io.ktor.plugin.features.DockerPortMappingProtocol.TCP
            )
        ))
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.bitcointracker.ApplicationKt"
    }
}

tasks.test {
    useJUnitPlatform()
}

// Define test source sets
sourceSets {
    create("integrationTest") {
        kotlin {
            compileClasspath += main.get().output + test.get().output
            runtimeClasspath += main.get().output + test.get().output
            srcDir("src/test/kotlin")
        }
    }
}

// Create configuration for integration tests
val integrationTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

// Define common test logging configuration
fun Test.configureTestLogging() {
    reports {
        html.required.set(true)
        junitXml.required.set(true)
    }
    
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        showExceptions = true
        showCauses = true
        showStackTraces = true
        displayGranularity = 2
        
        afterSuite(KotlinClosure2({ desc: TestDescriptor, result: TestResult ->
            if (desc.parent == null) {
                println("\nTest result: ${result.resultType}")
                println("Test summary: ${result.testCount} tests, " +
                        "${result.successfulTestCount} succeeded, " +
                        "${result.failedTestCount} failed, " +
                        "${result.skippedTestCount} skipped")
            }
        }))
    }
}

tasks {
    // Unit tests task
    register<Test>("unitTest") {
        description = "Runs unit tests"
        group = "verification"
        
        useJUnitPlatform()
        filter {
            includeTestsMatching("com.bitcointracker.unit.*")
        }
        
        configureTestLogging()
    }
    
    // Integration tests task
    register<Test>("integrationTest") {
        description = "Runs integration tests"
        group = "verification"

        outputs.upToDateWhen { false }
        
        useJUnitPlatform()
        filter {
            includeTestsMatching("com.bitcointracker.integration.*")
        }

        configureTestLogging()
    }
    
    // Modify the default test task
    test {
        description = "Runs all tests"
        useJUnitPlatform()
        
        // Optional: exclude integration tests from the default test task
        filter {
            excludeTestsMatching("com.bitcointracker.integration.*")
        }
        
        configureTestLogging()
    }
}