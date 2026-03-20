package conectaseguros.co.api.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SecurityHeadersGlobalFilter}.
 *
 * <p>Tests run WITHOUT Spring context — pure unit tests using
 * {@link MockServerWebExchange} and a mocked {@link GatewayFilterChain}.
 */
class SecurityHeadersGlobalFilterTest {

    private SecurityHeadersGlobalFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new SecurityHeadersGlobalFilter();
        chain = mock(GatewayFilterChain.class);
        // Chain always completes successfully
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("Pre-filter: request header sanitization")
    class RequestHeaderSanitization {

        @Test
        @DisplayName("Should remove X-Request-Source header from request")
        void removesXRequestSourceHeader() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/clients")
                    .header("X-Request-Source", "internal")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            // The chain receives a mutated exchange — we verify by capturing
            // Since MockServerWebExchange doesn't expose the mutated request directly,
            // we verify through the chain mock
            org.mockito.ArgumentCaptor<ServerWebExchange> captor =
                    org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
            org.mockito.Mockito.verify(chain).filter(captor.capture());

            ServerWebExchange forwarded = captor.getValue();
            assertThat(forwarded.getRequest().getHeaders().get("X-Request-Source")).isNull();
        }

        @Test
        @DisplayName("Should remove X-Service-Name header from request")
        void removesXServiceNameHeader() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products")
                    .header("X-Service-Name", "malicious-client")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            org.mockito.ArgumentCaptor<ServerWebExchange> captor =
                    org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
            org.mockito.Mockito.verify(chain).filter(captor.capture());

            ServerWebExchange forwarded = captor.getValue();
            assertThat(forwarded.getRequest().getHeaders().get("X-Service-Name")).isNull();
        }

        @Test
        @DisplayName("Should remove BOTH internal auth headers simultaneously")
        void removesBothInternalHeaders() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/payments")
                    .header("X-Request-Source", "internal")
                    .header("X-Service-Name", "fake-service")
                    .header("Authorization", "Bearer some-jwt-token")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            org.mockito.ArgumentCaptor<ServerWebExchange> captor =
                    org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
            org.mockito.Mockito.verify(chain).filter(captor.capture());

            ServerWebExchange forwarded = captor.getValue();
            HttpHeaders headers = forwarded.getRequest().getHeaders();
            assertThat(headers.get("X-Request-Source")).isNull();
            assertThat(headers.get("X-Service-Name")).isNull();
            // Legitimate headers should be preserved
            assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer some-jwt-token");
        }

        @Test
        @DisplayName("Should pass through requests without internal headers untouched")
        void passesThroughCleanRequests() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/news")
                    .header("Authorization", "Bearer valid-token")
                    .header("Content-Type", "application/json")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            org.mockito.ArgumentCaptor<ServerWebExchange> captor =
                    org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
            org.mockito.Mockito.verify(chain).filter(captor.capture());

            ServerWebExchange forwarded = captor.getValue();
            HttpHeaders headers = forwarded.getRequest().getHeaders();
            assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer valid-token");
            assertThat(headers.getFirst("Content-Type")).isEqualTo("application/json");
        }
    }

    @Nested
    @DisplayName("Post-filter: security response headers")
    class ResponseSecurityHeaders {

        @Test
        @DisplayName("Should add X-Content-Type-Options: nosniff to response")
        void addsXContentTypeOptions() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/clients").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(exchange.getResponse().getHeaders().getFirst("X-Content-Type-Options"))
                    .isEqualTo("nosniff");
        }

        @Test
        @DisplayName("Should add X-XSS-Protection: 0 to response")
        void addsXXssProtection() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/clients").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(exchange.getResponse().getHeaders().getFirst("X-XSS-Protection"))
                    .isEqualTo("0");
        }

        @Test
        @DisplayName("Should add Content-Security-Policy header to response")
        void addsContentSecurityPolicy() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/clients").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(exchange.getResponse().getHeaders().getFirst("Content-Security-Policy"))
                    .isEqualTo("default-src 'self'; frame-ancestors 'none'");
        }

        @Test
        @DisplayName("Should NOT duplicate response headers if they already exist (putIfAbsent)")
        void doesNotDuplicateExistingHeaders() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/clients").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Pre-set a header that the filter would add
            exchange.getResponse().getHeaders().put("X-Content-Type-Options",
                    List.of("already-set"));

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            // Should keep the original value, not overwrite
            List<String> values = exchange.getResponse().getHeaders().get("X-Content-Type-Options");
            assertThat(values).hasSize(1);
            assertThat(values.getFirst()).isEqualTo("already-set");
        }
    }

    @Nested
    @DisplayName("Filter ordering")
    class FilterOrdering {

        @Test
        @DisplayName("Should have order HIGHEST_PRECEDENCE + 1")
        void hasCorrectOrder() {
            assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
        }
    }
}
