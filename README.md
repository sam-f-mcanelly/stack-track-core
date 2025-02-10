# 🌐 HODL-TAX-CORE

> _"Where crypto meets code in the neon-lit world of tax compliance"_

[![Build Status](http://gitea:3000/sam/hodl-tax-core/actions/workflows/test.yml/badge.svg)](http://gitea:3000/sam/hodl-tax-core/actions)
[![Coverage](http://gitea:3000/sam/hodl-tax-core/badges/main/coverage.svg)](http://gitea:3000/sam/hodl-tax-core/coverage)
[![Docker](http://gitea:3000/sam/hodl-tax-core/badges/main/docker.svg)](http://gitea:3000/sam/hodl-tax-core/packages)

## 📡 Overview

HODL-TAX-CORE is a high-performance backend service designed for cryptocurrency tax calculations and portfolio tracking. Built with Kotlin, it leverages the power of Ktor for API serving, Gradle for build automation, and Docker for containerized deployment.

## 🔧 Tech Stack Integration

The service architecture integrates three primary technologies:

- **Ktor** serves as our asynchronous web framework, handling HTTP requests with coroutines for optimal performance. It provides the RESTful API endpoints and manages JSON serialization through Jackson.

- **Gradle** orchestrates our build process, managing dependencies and enabling modular testing through custom tasks for both unit and integration tests. It integrates with Kotlin's multiplatform capabilities and handles Docker image creation through the Ktor plugin.

- **Docker** containerizes the application, ensuring consistent deployment across environments. Through multi-stage builds, it optimizes the final image size while maintaining all runtime dependencies.

## 🔗 Dependencies

- Kotlin JVM 2.0.20
- Ktor 2.3.7
- Jackson (for JSON serialization)
- Exposed (SQL framework)
- H2 Database
- Dagger (DI framework)
- JUnit 5 (Testing)

## 🚀 Getting Started

### Prerequisites

1. **Java Installation**

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.1-amzn
```

2. **Gradle Installation**

```bash
sdk install gradle 8.5
```

### Running with Ktor Docker Plugin

The service can be run directly using the Ktor Docker plugin, which handles both building and running the container:

```bash
# Build Docker image using Ktor plugin
./gradlew runDocker

```

The Ktor Docker plugin configuration in `build.gradle.kts`:
```kotlin
ktor {
    docker {
        localImageName.set("hodl-tax-core")
        imageTag.set("0.0.1-preview")
        jreVersion.set(JavaVersion.VERSION_21)
        
        portMappings.set(listOf(
            io.ktor.plugin.features.DockerPortMapping(
                90,    // Host port
                9090,  // Container port
                io.ktor.plugin.features.DockerPortMappingProtocol.TCP
            )
        ))
        
        environmentVariables.set(mapOf(
            "JAVA_OPTS" to "-Xms256m -Xmx512m",
            "TZ" to "UTC"
        ))
    }
}
```

This method provides a streamlined way to build and run the service, with the Ktor plugin handling:
- Docker image creation
- Port mappings
- Environment variables
- JVM configuration
- Health checks

## 🚀 Release Process [WORK IN PROGRESS]

The project uses GitHub Actions for automated releases. When a tag is pushed, it:
1. Builds the project
2. Runs tests
3. Creates a GitHub release
4. Builds and pushes Docker image

## 🔍 Health Check

The service includes a health endpoint at `/health` that returns a 200 OK status when operational.

## 🛠️ Development

- Run tests: `./gradlew test`
- Build Docker image: `docker buildx build --load -t localhost:3002/sam/hodl-tax-core:latest .`
- Start service: `docker compose up -d`
- View logs: `docker compose logs -f`

## 📡 Network Configuration

The service operates on port 9090 internally and is mapped to port 90 on the host machine. It's part of the `hodl-tax` Docker network for service discovery and isolation.

## 🔗 Dependencies

- Kotlin JVM 2.0.20
- Ktor 2.3.7
- Jackson (for JSON serialization)
- Exposed (SQL framework)
- H2 Database
- Dagger (DI framework)
- JUnit 5 (Testing)

## 🔗 Clone the repository

```bash
git clone https://localhost:3001/sam/hodl-tax-core.git

```

---


