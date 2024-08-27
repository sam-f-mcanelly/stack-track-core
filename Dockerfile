# Use an official OpenJDK runtime as a parent image
FROM openjdk:17-jdk-slim

# Set the working directory in the container
WORKDIR /app

# Copy the Gradle build files and source code
COPY . .

# Build the application
RUN ./gradlew build

# Expose the application port
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "build/libs/bitcoin-tracker-1.0.0.jar"]