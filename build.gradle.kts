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