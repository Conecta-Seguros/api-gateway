plugins {
	java
	id("org.springframework.boot") version "4.0.3"
	id("io.spring.dependency-management") version "1.1.7"
	jacoco
}
val springCloudVersion by extra("2025.1.1")

group = "conectaseguros.co"
version = "1.0.0"

springBoot {
    mainClass.set("conectaseguros.co.api.gateway.ApiGatewayApplication")
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// Spring Boot dependencies
	implementation("org.springframework.boot:spring-boot-configuration-processor")
	implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.boot:spring-boot-configuration-metadata")

	// Dotenv support
	implementation("io.github.cdimascio:dotenv-java")

    // Annotations
    implementation("org.jetbrains:annotations")

    // Caffeine for caching
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Resilience4j for circuit breaking
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j")

    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

	// Annotations and utilities
	annotationProcessor("org.projectlombok:lombok")
	compileOnly("org.projectlombok:lombok")

    // Development dependencies
	"developmentOnly"("org.springframework.boot:spring-boot-devtools")

    // Monitoring dependencies
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

	// Testing dependencies
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("io.projectreactor:reactor-test")
	testImplementation("org.springframework.security:spring-security-test")
	testAnnotationProcessor("org.projectlombok:lombok")
	testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
	}
	dependencies {
		dependency("io.github.cdimascio:dotenv-java:3.2.0")
        dependency("org.springframework.boot:spring-boot-configuration-metadata:4.0.3")
        dependency("org.springframework.security:spring-security-oauth2-jose:7.0.4")
        dependency("org.jetbrains:annotations:26.1.0")
        dependency("com.github.ben-manes.caffeine:caffeine:3.2.3")
	}
}

jacoco {
	toolVersion = "0.8.14"
}

tasks.test {
	finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required = true
		html.required = true
	}
}

val toolchainVersion = java.toolchain.languageVersion.get().asInt()
val jvmCompatArgs = buildList {
	add("-Xshare:off")
	if (toolchainVersion >= 23) {
		add("--sun-misc-unsafe-memory-access=allow")
	}
}

tasks.bootRun {
	jvmArgs(jvmCompatArgs)
}

tasks.withType<Test> {
	useJUnitPlatform()
	jvmArgs(jvmCompatArgs)
}