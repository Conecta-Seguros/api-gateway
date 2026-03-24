# -- Build Args --
ARG JAVA_VERSION=25
ARG APP_VERSION=1.0.0

# ============================================
# STAGE 1: Build
# ============================================
FROM gradle:jdk${JAVA_VERSION}-alpine AS builder

WORKDIR /build

COPY build.gradle.kts settings.gradle.kts ./
COPY gradle.properties* ./

RUN gradle dependencies --no-daemon --stacktrace || true

COPY src/ src/

RUN gradle clean bootJar --no-daemon --stacktrace -x test && \
    test $(ls /build/build/libs/*.jar 2>/dev/null | wc -l) -eq 1

# ============================================
# STAGE 2: Layer Extraction
# ============================================
FROM eclipse-temurin:${JAVA_VERSION}-jdk-alpine AS extractor

WORKDIR /extract

COPY --from=builder /build/build/libs/*.jar application.jar

RUN java -Djarmode=layertools -jar application.jar extract --destination layers/ && \
    test -d layers/dependencies && \
    test -d layers/spring-boot-loader && \
    test -d layers/application && \
    mkdir -p layers/snapshot-dependencies

# ============================================
# STAGE 3: Runtime
# ============================================
FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine AS runtime

# -- Metadata --
ARG APP_VERSION
LABEL maintainer="Caicedo Seguros <desarrollo@caicedoseguros.com>" \
      org.opencontainers.image.title="API Gateway" \
      org.opencontainers.image.description="Spring Cloud Gateway — routing, security, and cross-cutting concerns" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.vendor="Caicedo Seguros" \
      org.opencontainers.image.source="https://github.com/Conecta-Seguros/api-gateway"

# -- System setup --
RUN apk add --no-cache \
      wget \
      tzdata \
    && apk upgrade --no-cache \
    && cp /usr/share/zoneinfo/America/Bogota /etc/localtime \
    && echo "America/Bogota" > /etc/timezone \
    && apk del tzdata \
    && addgroup -S -g 1001 spring \
    && adduser -S -u 1001 -G spring -s /sbin/nologin spring \
    && mkdir -p /app \
    && chown -R spring:spring /app \
    && rm -rf /var/cache/apk/*

WORKDIR /app

# -- Copy layers --
COPY --from=extractor --chown=spring:spring /extract/layers/dependencies/ ./
COPY --from=extractor --chown=spring:spring /extract/layers/spring-boot-loader/ ./
COPY --from=extractor --chown=spring:spring /extract/layers/snapshot-dependencies/ ./
COPY --from=extractor --chown=spring:spring /extract/layers/application/ ./

# -- Security --
USER spring:spring

# -- Port --
EXPOSE 8080

# -- Health check --
HEALTHCHECK --interval=30s \
            --timeout=10s \
            --start-period=120s \
            --retries=5 \
    CMD wget -q --spider http://localhost:8080/actuator/health/liveness || exit 1

# -- Environment --
ENV JAVA_OPTS="" \
    SPRING_PROFILES_ACTIVE=k8s-ha \
    SPRING_MAIN_CLOUD_PLATFORM=kubernetes

# -- JVM Defaults (optimized for reactive/Netty workloads) --
ENV JAVA_TOOL_OPTIONS="\
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom \
    -Djava.awt.headless=true \
    -Dfile.encoding=UTF-8 \
    -Dreactor.netty.ioWorkerCount=4 \
    -Dreactor.netty.pool.maxConnections=500"

# -- Entrypoint --
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
