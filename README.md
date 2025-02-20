# stack-track-core 🚀

A lightning-fast backend service for stack and portfolio tracking, built with Kotlin and Ktor.

[![Build Status](https://img.shields.io/github/workflow/status/yourusername/stack-track-core/CI)](https://github.com/yourusername/stack-track-core/actions)
[![Coverage](https://img.shields.io/codecov/c/github/yourusername/stack-track-core)](https://codecov.io/gh/yourusername/stack-track-core)
[![Docker Pulls](https://img.shields.io/docker/pulls/yourusername/stack-track-core)](https://hub.docker.com/r/yourusername/stack-track-core)
[![License](https://img.shields.io/github/license/yourusername/stack-track-core)](https://github.com/yourusername/stack-track-core/blob/main/LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.20-blue.svg)](https://kotlinlang.org)
[![Ktor](https://img.shields.io/badge/ktor-2.3.7-blue.svg)](https://ktor.io)

## What's This? 🤔

stack-track-core is your friendly neighborhood portfolio tracking service. Built with Kotlin and powered by Ktor, it makes managing and analyzing your investments a breeze. This is the backend service that needs to be paired with Stack Track frontend for full functionality.

## Tech Stack 🛠️

- 💻 Kotlin 2.0.20
- 🌐 Ktor 2.3.7 (Web framework)
- 🗄️ Exposed (SQL framework)
- 📊 H2 Database
- 🎯 Dagger (Dependency injection)
- 🧪 JUnit 5 (Testing)
- 📦 Jackson (JSON serialization)
- 🐳 Docker

## Prerequisites ✨

- Java 21 (Amazon Corretto)
- Gradle 8.5

## Get Started 🚀

1. Clone this bad boy:
```bash
git clone https://github.com/sam-f-mcanelly/stack-track-core
cd stack-track-core
```

2. Get Java and Gradle using SDKMAN:
```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.1-amzn
sdk install gradle 8.5
```

## Development 👩‍💻

Build and test:
```bash
./gradlew build
./gradlew test
```

Fire it up locally (requires Docker):
```bash
./gradlew runDocker
```

## Docker Magic 🐳

Build that image:
```bash
docker buildx build --load -t localhost:3002/sam/stack-track-core:latest .
```

### Ktor Docker Config 🔧

Here's how we containerize with Ktor's Docker plugin (`build.gradle.kts`):

```kotlin
ktor {
    docker {
        localImageName.set("stack-track-core")
        imageTag.set("0.0.1-preview")
        jreVersion.set(JavaVersion.VERSION_21)
        
        portMappings.set(listOf(
            io.ktor.plugin.features.DockerPortMapping(
                90,    // Host port
                9090,  // Container port
                io.ktor.plugin.features.DockerPortMappingProtocol.TCP
            )
        ))
    }
}
```

Run it with Ktor's Docker plugin:
```bash
./gradlew runDocker
```

## Network Details 🌐

- Internal port: 9090
- Host port mapping: 90
- Docker network: stack-track

## Health Check 💓

Hit `/health` for a quick HTTP 200 when everything's running smooth.

## CI/CD Pipeline 🔄

We've got GitHub Actions doing the heavy lifting:
- Builds and tests on every push
- Creates releases from tags
- Publishes Docker images
- Tracks code coverage

## License 📜

[Add your license information here]

---

Made with ❤️ by the Stack Track team