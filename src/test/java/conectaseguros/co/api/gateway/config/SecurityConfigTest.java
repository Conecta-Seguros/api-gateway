package conectaseguros.co.api.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration test for {@link SecurityConfig} authorization rules.
 *
 * <p>Loads the full Spring context with the test profile to verify that:
 * <ul>
 *   <li>Health endpoints are publicly accessible (Kubernetes probes)</li>
 *   <li>Fallback endpoints are publicly accessible (circuit breaker)</li>
 *   <li>Protected endpoints require JWT authentication</li>
 * </ul>
 *
 * <p>The {@link ReactiveJwtDecoder} is mocked to avoid connecting to Keycloak.
 * Gateway routing and Eureka are disabled via test properties.
 *
 * <p>In Spring Boot 4.0, {@code @AutoConfigureWebTestClient} was removed.
 * {@link WebTestClient} is created manually from the {@link ApplicationContext}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.gateway.enabled=false",
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379"
        }
)
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @MockitoBean
    private ReactiveClientRegistrationRepository clientRegistrationRepository;

    private WebTestClient webTestClient() {
        return WebTestClient.bindToApplicationContext(applicationContext)
                .build();
    }

    @Nested
    @DisplayName("Public endpoints — no authentication required")
    class PublicEndpoints {

        @Test
        @DisplayName("GET /actuator/health is accessible without authentication")
        void healthEndpointIsPublic() {
            // Note: We verify the endpoint is NOT blocked by security (not 401/403).
            // The actual status may be 503 if downstream health indicators (Redis) are down.
            webTestClient().get().uri("/actuator/health")
                    .exchange()
                    .expectStatus().value(status ->
                            assertThat(status).isNotIn(401, 403)
                    );
        }

        @Test
        @DisplayName("GET /actuator/health/liveness is accessible without authentication")
        void livenessProbeIsPublic() {
            webTestClient().get().uri("/actuator/health/liveness")
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("GET /actuator/health/readiness is accessible without authentication")
        void readinessProbeIsPublic() {
            webTestClient().get().uri("/actuator/health/readiness")
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("POST /fallback/service-unavailable is accessible without authentication")
        void fallbackEndpointIsPublic() {
            webTestClient().post().uri("/fallback/service-unavailable")
                    .exchange()
                    .expectStatus().value(status ->
                            assertThat(status).isNotIn(401, 403)
                    );
        }
    }

    @Nested
    @DisplayName("Protected endpoints — authentication required")
    class ProtectedEndpoints {

        @Test
        @DisplayName("GET /actuator/metrics requires authentication (returns 401)")
        void metricsEndpointRequiresAuth() {
            webTestClient().get().uri("/actuator/metrics")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("GET /actuator/prometheus requires authentication (returns 401)")
        void prometheusEndpointRequiresAuth() {
            webTestClient().get().uri("/actuator/prometheus")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("GET /actuator/info requires authentication (returns 401)")
        void infoEndpointRequiresAuth() {
            webTestClient().get().uri("/actuator/info")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("GET /api/v1/clients requires authentication (returns 401)")
        void apiEndpointRequiresAuth() {
            webTestClient().get().uri("/api/v1/clients")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }
}
