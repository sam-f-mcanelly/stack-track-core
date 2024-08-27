plugins {
    kotlin("jvm") version "2.0.20"
    application
    id("org.jetbrains.kotlin.kapt") version "2.0.20"
    // id("org.jetbrains.kotlin.plugin.serialization")
}

group = "com.bitcointracker"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
}

//application {
//    // This is necessary to ensure the correct entry point is used.
//    mainClass.set("MainKt") // Kotlin compiles 'Main.kt' to 'MainKt.class'
//}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.ktor:ktor-server-netty:2.3.1")
    implementation("io.ktor:ktor-server-core:2.3.1")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.1")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.google.code.gson:gson:2.8.8")
    implementation("com.google.dagger:dagger:2.48")
    kapt("com.google.dagger:dagger-compiler:2.48")
    implementation("javax.inject:javax.inject:1")
}

application {
    mainClass.set("ApplicationKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "ApplicationKt"
    }
}

//tasks.register<JavaExec>("runCsvParser") {
//    group = "application"
//    description = "Runs the CSV parser with the specified file location"
//    mainClass.set("MainKt") // Ensure the main class is set
//    classpath = sourceSets["main"].runtimeClasspath
//
//    doFirst {
//        val folder = project.properties["folder"] as? String
//            ?: throw GradleException("Please provide the file location as a project property.")
//        args = listOf(folder)
//    }
//}