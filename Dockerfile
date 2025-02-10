FROM gradle:latest AS cache
RUN echo "Setting up gradle..."
RUN mkdir -p /home/gradle/cache_home
ENV GRADLE_USER_HOME=/home/gradle/cache_home
COPY build.gradle.* settings.gradle.* /home/gradle/app/
WORKDIR /home/gradle/app
RUN gradle clean build -i --stacktrace

# Stage 2: Build Application
RUN echo "Building the application..."
FROM gradle:latest AS build
COPY --from=cache /home/gradle/cache_home /home/gradle/.gradle
COPY . /usr/src/app/
WORKDIR /usr/src/app
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
# Build the fat JAR, Gradle also supports shadow
# and boot JAR by default.
RUN gradle buildFatJar --no-daemon

# Stage 3: Create the Runtime Image
RUN echo "Starting the application on port 8080..."
FROM amazoncorretto:22 AS runtime
EXPOSE 9090
RUN yum install -y curl-minimal
# Add health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:90/health || exit 1
RUN mkdir /app
# copy the fat jar
COPY --from=build /home/gradle/src/build/libs/*-all.jar /app/hodl-tax-core-1.0.0.jar
ENTRYPOINT ["java","-jar","/app/hodl-tax-core-1.0.0.jar"]