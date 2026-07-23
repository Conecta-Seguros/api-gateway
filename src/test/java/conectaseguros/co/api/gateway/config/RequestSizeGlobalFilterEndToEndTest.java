package conectaseguros.co.api.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.unit.DataSize;

/**
 * True end-to-end integration test for {@link RequestSizeGlobalFilter}.
 *
 * <p>Closes the Integration-layer gap flagged by sdd-verify (obs #327): the design's own
 * Testing Strategy table (obs #320) specified an {@code @SpringBootTest} layer that boots
 * the gateway context and drives real HTTP-shaped requests through it, but only the Unit
 * layer ({@link RequestSizeGlobalFilterTest}, hand-built {@link GatewayLimitsProperties} +
 * mocked {@code GatewayFilterChain}) had been implemented.
 *
 * <p>This test instead:
 * <ul>
 *   <li>boots the REAL Spring Boot application context with gateway routing ENABLED
 *       (unlike {@link ApiGatewayApplicationTest} / {@link SecurityConfigTest}, which
 *       disable it), so {@link GatewayLimitsProperties#maxRequestSize} is bound by the
 *       real {@code Binder} from {@code application.properties} — not constructed by hand;</li>
 *   <li>routes to a synthetic, publicly-accessible {@code forward:} target
 *       ({@link FallbackController}, the same mechanism the production CircuitBreaker
 *       filter's {@code fallbackUri} already uses) so the request genuinely traverses
 *       {@code RoutePredicateHandlerMapping} + {@code FilteringWebHandler} — where
 *       GlobalFilters actually execute — instead of falling through to a directly-mapped
 *       controller, which would never invoke gateway filters at all;</li>
 *   <li>exercises the FULL default filter chain (Spring Security authorization,
 *       {@code RequestSizeGlobalFilter}, {@code SecurityHeadersGlobalFilter}, route
 *       forwarding), not the isolated filter.</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379",
                "spring.cloud.gateway.server.webflux.routes[0].id=request-size-e2e-probe",
                "spring.cloud.gateway.server.webflux.routes[0].uri=forward:/fallback/service-unavailable",
                "spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/fallback/probe/**"
        }
)
@ActiveProfiles("test")
class RequestSizeGlobalFilterEndToEndTest {

    private static final long FIFTY_FIVE_MB_IN_BYTES = 55L * 1024 * 1024;
    private static final String PROBE_URI = "/fallback/probe/archivos/cargar";

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private GatewayLimitsProperties gatewayLimitsProperties;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @MockitoBean
    private ReactiveClientRegistrationRepository clientRegistrationRepository;

    private WebTestClient webTestClient() {
        return WebTestClient.bindToApplicationContext(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Real Spring Boot binder resolves app.gateway.max-request-size to exactly 55MB")
    void resolvesConfiguredLimitToFiftyFiveMb() {
        assertThat(gatewayLimitsProperties.getMaxRequestSize()).isEqualTo(DataSize.ofMegabytes(55));
        assertThat(gatewayLimitsProperties.getMaxRequestSize().toBytes()).isEqualTo(FIFTY_FIVE_MB_IN_BYTES);
    }

    @Test
    @DisplayName("E2E: oversized request is rejected 413 by the real filter chain before reaching the route")
    void oversizedRequestRejectedThroughRealFilterChain() {
        webTestClient()
                .post().uri(PROBE_URI)
                .header("Content-Length", String.valueOf(FIFTY_FIVE_MB_IN_BYTES + 1))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONTENT_TOO_LARGE)
                .expectHeader().exists("errorMessage");
    }

    @Test
    @DisplayName("E2E: request at the exact 55MB boundary is forwarded to the downstream route")
    void boundaryRequestForwardedThroughRealFilterChain() {
        webTestClient()
                .post().uri(PROBE_URI)
                .header("Content-Length", String.valueOf(FIFTY_FIVE_MB_IN_BYTES))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.path").isEqualTo("/fallback/service-unavailable");
    }

    @Test
    @DisplayName("E2E: request under the 55MB cap is forwarded to the downstream route")
    void underLimitRequestForwardedThroughRealFilterChain() {
        webTestClient()
                .post().uri(PROBE_URI)
                .header("Content-Length", String.valueOf(30L * 1024 * 1024))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.path").isEqualTo("/fallback/service-unavailable");
    }
}
