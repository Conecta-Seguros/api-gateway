# ============================================
# STAGE 1: Build Stage
# ============================================
FROM gradle:jdk25-alpine AS builder

# Metadata
LABEL stage=builder
LABEL description="Build stage for API Gateway"

# Set working directory
WORKDIR /build

# Copy only Gradle configuration files first (for better caching)
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle.properties ./

# Download dependencies (this layer will be cached unless build files change)
RUN gradle dependencies --no-daemon --stacktrace || return 0

# Copy source code
COPY src src/

# Build the application (skip tests for faster builds - tests run in CI/CD)
RUN gradle clean bootJar --no-daemon --stacktrace -x test

# Verify JAR was created
RUN ls -la /build/build/libs/ && \
    test -f /build/build/libs/*.jar && \
    echo "✅ JAR file created successfully"

# ============================================
# STAGE 2: Layer Extraction Stage
# ============================================
FROM eclipse-temurin:25-jdk-alpine AS extractor

LABEL stage=extractor
LABEL description="Layer extraction stage for optimized Docker layers"

WORKDIR /extract

# Copy the JAR from builder stage
COPY --from=builder /build/build/libs/*.jar application.jar

# Verify JAR file
RUN test -f application.jar && \
    echo "✅ JAR file found successfully"

# Extract Spring Boot layers for optimal caching
# Spring Boot 3.5+ uses the new layertools command
RUN java -Djarmode=layertools -jar application.jar extract --destination extracted/

# Verify layers were extracted
RUN ls -la extracted/ && \
    test -d extracted/dependencies && \
    test -d extracted/spring-boot-loader && \
    test -d extracted/application && \
    echo "✅ Layers extracted successfully"

# ============================================
# STAGE 3: Final Runtime Image
# ============================================
FROM eclipse-temurin:25-jre-alpine AS runtime

# Metadata
LABEL maintainer="Conecta Seguros <team@conecta-seguros.com>"
LABEL service="api-gateway"
LABEL description="Spring Cloud Gateway for microservices routing and security"
LABEL version="1.0.0"
LABEL java.version="25"
LABEL gradle.version="9.1.0"
LABEL spring-boot.version="3.5.6"

# Install required packages and security updates
RUN apk add --no-cache \
    curl \
    wget \
    tzdata \
    && apk upgrade --no-cache \
    && rm -rf /var/cache/apk/*

# Set timezone (adjust to your timezone)
ENV TZ=America/Bogota
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Create a non-root user and group for security
RUN addgroup -S -g 1001 spring && \
    adduser -S -u 1001 -G spring spring && \
    mkdir -p /app && \
    chown -R spring:spring /app

# Set working directory
WORKDIR /app

# Copy extracted layers from extractor stage with proper ownership
COPY --from=extractor --chown=spring:spring /extract/extracted/dependencies/ ./
COPY --from=extractor --chown=spring:spring /extract/extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=spring:spring /extract/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=spring:spring /extract/extracted/application/ ./

# Switch to non-root user
USER spring:spring

# Expose API Gateway port
EXPOSE 8088

# Add comprehensive health check for API Gateway
# Note: API Gateway health check might require longer start period due to Eureka registration
HEALTHCHECK --interval=30s \
            --timeout=10s \
            --start-period=120s \
            --retries=5 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM optimization flags for API Gateway (WebFlux reactive application)
# API Gateway uses Netty and WebFlux, so we optimize for reactive workloads
ENV JAVA_OPTS="\
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:ParallelGCThreads=2 \
    -XX:ConcGCThreads=1 \
    -XX:+UseStringDeduplication \
    -XX:+OptimizeStringConcat \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+UseZGC \
    -Djava.security.egd=file:/dev/./urandom \
    -Djava.awt.headless=true \
    -Dfile.encoding=UTF-8 \
    -Dspring.backgroundpreinitializer.ignore=true \
    -Dreactor.netty.ioWorkerCount=4 \
    -Dreactor.netty.pool.maxConnections=500"

# Use Spring Boot's JarLauncher for layered execution
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]