FROM eclipse-temurin:17-jdk-alpine

# Install required packages including Node.js
RUN apk add --no-cache \
    curl \
    python3 \
    py3-pip \
    ffmpeg \
    nodejs \
    npm

# Install yt-dlp
RUN curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp \
    && chmod a+rx /usr/local/bin/yt-dlp

# Verify installations
RUN yt-dlp --version && node --version

WORKDIR /app

# Copy gradle files first (for better caching)
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle ./gradle

# Download dependencies
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src ./src

# Build the application
RUN ./gradlew bootJar --no-daemon

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "build/libs/demo-0.0.1-SNAPSHOT.jar"]