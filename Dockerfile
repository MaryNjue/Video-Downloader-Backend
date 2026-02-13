FROM eclipse-temurin:17-jdk-alpine

# Install required packages
RUN apk add --no-cache \
    curl \
    python3 \
    py3-pip \
    ffmpeg \
    unzip \
    bash

# Install yt-dlp
RUN curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp \
    && chmod a+rx /usr/local/bin/yt-dlp

# Install Deno - Alpine Linux needs musl build, not glibc
RUN curl -fsSL https://github.com/denoland/deno/releases/download/v2.2.2/deno-x86_64-unknown-linux-musl.zip -o /tmp/deno.zip \
    && unzip /tmp/deno.zip -d /usr/local/bin/ \
    && chmod +x /usr/local/bin/deno \
    && rm /tmp/deno.zip \
    && /usr/local/bin/deno --version

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