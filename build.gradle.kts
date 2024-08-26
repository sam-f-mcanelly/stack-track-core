plugins {
    kotlin("jvm") version "2.0.20"
    application
    id("org.jetbrains.kotlin.kapt") version "2.0.20"
}

group = "com.bitcointracker"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
}

application {
    // This is necessary to ensure the correct entry point is used.
    mainClass.set("MainKt") // Kotlin compiles 'Main.kt' to 'MainKt.class'
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.google.code.gson:gson:2.8.8")
    implementation("com.google.dagger:dagger:2.48")
    kapt("com.google.dagger:dagger-compiler:2.48")
    implementation("javax.inject:javax.inject:1")
}

tasks.register<JavaExec>("runCsvParser") {
    group = "application"
    description = "Runs the CSV parser with the specified file location"
    mainClass.set("MainKt") // Ensure the main class is set
    classpath = sourceSets["main"].runtimeClasspath

    doFirst {
        val folder = project.properties["folder"] as? String
            ?: throw GradleException("Please provide the file location as a project property.")
        args = listOf(folder)
    }
}