plugins {
    kotlin("jvm") version "2.0.0"
    application
}

group = "com.bitcointracker"
version = "1.0.0"

repositories {
    mavenCentral()
}

application {
    // This is necessary to ensure the correct entry point is used.
    mainClass.set("MainKt") // Kotlin compiles 'Main.kt' to 'MainKt.class'
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.amazonaws:aws-lambda-java-core:1.2.1")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.0")
    // implementation("software.amazon.awssdk:dynamodb:2.20.89")
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

// Lambda specific?
// tasks {
//     val jar by getting(Jar::class) {
//         manifest {
//             attributes["Main-Class"] = "com.bitcointracker.FileDropHandler"
//         }

//         // Include the compiled classes and resources in the jar
//         from(sourceSets.main.get().output)

//         // Include dependencies
//         from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
//     }

//     // Ensure that the build task depends on the jar task
//     build {
//         dependsOn(jar)
//     }
// }