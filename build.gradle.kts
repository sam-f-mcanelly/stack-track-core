plugins {
    kotlin("jvm") version "2.0.20"
    application
    id("org.jetbrains.kotlin.kapt") version "2.0.20"
    kotlin("plugin.serialization") version "1.8.10"
    id("io.ktor.plugin") version "2.3.12"
    id("com.github.johnrengelman.shadow") version "8.1.1"
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
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.1")
    implementation("io.ktor:ktor-server-cors")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.google.code.gson:gson:2.8.8")
    implementation("com.google.dagger:dagger:2.48")
    kapt("com.google.dagger:dagger-compiler:2.48")
    implementation("javax.inject:javax.inject:1")
    implementation("org.slf4j:slf4j-api:2.0.9")
}

application {
    mainClass.set("ApplicationKt")
}

ktor {
    docker {
        localImageName.set("coin-cortex-core")
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