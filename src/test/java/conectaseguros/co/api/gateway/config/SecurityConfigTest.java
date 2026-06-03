package conectaseguros.co.api.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
 * <p>Most tests use {@link WebTestClient#bindToApplicationContext} with {@code mockJwt()}
 * for fast in-process verification. The CORS header test uses {@link WebTestClient#bindToServer}
 * against the real Netty server because Spring Framework 7's {@code CorsUtils.isCorsRequest()}
 * now performs same-origin detection — which does not behave correctly in the synthetic mock
 * request context used by {@code bindToApplicationContext}.
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

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @MockitoBean
    private ReactiveClientRegistrationRepository clientRegistrationRepository;

    private WebTestClient webTestClient() {
        return WebTestClient.bindToApplicationContext(applicationContext)
                .apply(springSecurity())
                .build();
    }

    private WebTestClient serverClient() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
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

    @Nested
    @DisplayName("Role-based access control")
    class RoleBasedAccessControl {

        @BeforeEach
        void configureMockDecoder() {
            Jwt consultantJwt = Jwt.withTokenValue("test-consultant-token")
                    .header("alg", "RS256")
                    .claim("realm_access", Map.of("roles", List.of("CONSULTANT")))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
            given(reactiveJwtDecoder.decode("test-consultant-token"))
                    .willReturn(Mono.just(consultantJwt));
        }

        @Test
        @DisplayName("CONSULTANT: POST /api/v1/** returns 403 Forbidden")
        void consultantCannotPost() {
            webTestClient()
                    .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CONSULTANT")))
                    .post().uri("/api/v1/clients")
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @DisplayName("CONSULTANT: PUT /api/v1/** returns 403 Forbidden")
        void consultantCannotPut() {
            webTestClient()
                    .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CONSULTANT")))
                    .put().uri("/api/v1/clients/1")
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @DisplayName("CONSULTANT: DELETE /api/v1/** returns 403 Forbidden")
        void consultantCannotDelete() {
            webTestClient()
                    .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CONSULTANT")))
                    .delete().uri("/api/v1/clients/1")
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @DisplayName("CONSULTANT: GET /api/v1/** is not blocked by security")
        void consultantCanGet() {
            webTestClient()
                    .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CONSULTANT")))
                    .get().uri("/api/v1/clients")
                    .exchange()
                    .expectStatus().value(status ->
                            assertThat(status).isNotIn(401, 403)
                    );
        }

        @Test
        @DisplayName("ADMIN: POST /api/v1/** is not blocked by security")
        void adminCanPost() {
            webTestClient()
                    .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                    .post().uri("/api/v1/clients")
                    .exchange()
                    .expectStatus().value(status ->
                            assertThat(status).isNotIn(401, 403)
                    );
        }

        @Test
        @DisplayName("CARTERA: POST /api/v1/** is not blocked by security")
        void carteraCanPost() {
            webTestClient()
                    .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CARTERA")))
                    .post().uri("/api/v1/clients")
                    .exchange()
                    .expectStatus().value(status ->
                            assertThat(status).isNotIn(401, 403)
                    );
        }

        @Test
        @DisplayName("CONSULTANT: 403 response has structured JSON body")
        void consultantForbiddenHasStructuredBody() {
            webTestClient()
                    .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CONSULTANT")))
                    .post().uri("/api/v1/clients")
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectBody()
                    .jsonPath("$.message").isEqualTo("You do not have permission to perform this action.")
                    .jsonPath("$.status").isEqualTo(403)
                    .jsonPath("$.timestamp").isNotEmpty()
                    .jsonPath("$.path").isEqualTo("/api/v1/clients");
        }

        /**
         * Uses {@code bindToServer()} against the real Netty server because
         * Spring Framework 7's {@code CorsUtils.isCorsRequest()} now includes same-origin
         * detection that does not work correctly in the synthetic request context used by
         * {@code bindToApplicationContext()}. A real HTTP request from a different port
         * (Origin: localhost:3000 vs server port) is required for the CORS filter to
         * correctly classify it as cross-origin and add the ACAO header.
         */
        @Test
        @DisplayName("CONSULTANT: 403 response includes CORS header when Origin is present")
        void consultantForbiddenHasCorsHeader() {
            serverClient()
                    .post().uri("/api/v1/clients")
                    .header("Authorization", "Bearer test-consultant-token")
                    .header("Origin", "http://localhost:3000")
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectHeader().exists("Access-Control-Allow-Origin");
        }
    }
}
