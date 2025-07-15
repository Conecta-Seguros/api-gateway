plugins {
	java
	id("org.springframework.boot") version "3.5.3"
	id("io.spring.dependency-management") version "1.1.7"
}
val springCloudVersion by extra("2025.0.0")

group = "conectaseguros.co"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(24)
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
	implementation("org.springframework.boot:spring-boot-starter-security")

	// Annotations and utilities
	implementation("org.projectlombok:lombok")

	// Dotenv support
	implementation("io.github.cdimascio:dotenv-java")

	// Development dependencies
	"developmentOnly"("org.springframework.boot:spring-boot-devtools")

	// Testing dependencies
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
	}
	dependencies {
		dependency("io.github.cdimascio:dotenv-java:3.2.0")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
