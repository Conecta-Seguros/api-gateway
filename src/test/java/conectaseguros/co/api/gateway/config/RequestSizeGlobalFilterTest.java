package conectaseguros.co.api.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link RequestSizeGlobalFilter}.
 *
 * <p>Tests run WITHOUT Spring context — pure unit tests using
 * {@link MockServerWebExchange} and a mocked {@link GatewayFilterChain}, matching the
 * style of {@link SecurityHeadersGlobalFilterTest}.
 */
class RequestSizeGlobalFilterTest {

    private static final long FIFTY_FIVE_MB_IN_BYTES = 55L * 1024 * 1024;

    private RequestSizeGlobalFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        GatewayLimitsProperties properties = new GatewayLimitsProperties();
        properties.setMaxRequestSize(DataSize.ofBytes(FIFTY_FIVE_MB_IN_BYTES));
        filter = new RequestSizeGlobalFilter(properties);

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("Requests exceeding the configured limit")
    class RequestsExceedingLimit {

        @Test
        @DisplayName("Should reject with 413 when Content-Length is over the limit")
        void rejectsOversizedRequestWith413() {
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/news/archivos/cargar")
                    .header("Content-Length", String.valueOf(FIFTY_FIVE_MB_IN_BYTES + 1))
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        }

        @Test
        @DisplayName("Should include an errorMessage header describing the limit when rejecting")
        void includesErrorMessageHeaderOnRejection() {
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/news/archivos/cargar")
                    .header("Content-Length", String.valueOf(FIFTY_FIVE_MB_IN_BYTES + 1))
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            String errorMessage = exchange.getResponse().getHeaders().getFirst("errorMessage");
            assertThat(errorMessage).isNotNull();
            assertThat(errorMessage).contains("Request size is larger than permissible limit");
        }

        @Test
        @DisplayName("Should NOT invoke the downstream chain when rejecting")
        void doesNotInvokeChainOnRejection() {
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/news/archivos/cargar")
                    .header("Content-Length", String.valueOf(FIFTY_FIVE_MB_IN_BYTES + 1))
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verify(chain, never()).filter(any(ServerWebExchange.class));
        }
    }

    @Nested
    @DisplayName("Requests within the configured limit")
    class RequestsWithinLimit {

        @Test
        @DisplayName("Should forward to the chain when Content-Length equals the limit exactly (boundary)")
        void forwardsRequestAtExactBoundary() {
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/news/archivos/cargar")
                    .header("Content-Length", String.valueOf(FIFTY_FIVE_MB_IN_BYTES))
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verify(chain).filter(exchange);
            assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        }

        @Test
        @DisplayName("Should forward to the chain when Content-Length is under the limit")
        void forwardsRequestUnderLimit() {
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/news/archivos/cargar")
                    .header("Content-Length", String.valueOf(30L * 1024 * 1024))
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verify(chain).filter(exchange);
            assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        }

        @Test
        @DisplayName("Should forward to the chain when Content-Length header is absent")
        void forwardsRequestWithoutContentLengthHeader() {
            MockServerHttpRequest request =
                    MockServerHttpRequest.post("/api/v1/news/archivos/cargar").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verify(chain).filter(exchange);
            assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        }
    }

    @Nested
    @DisplayName("Malformed Content-Length header")
    class MalformedContentLength {

        @Test
        @DisplayName("Should reject with 400 instead of throwing when Content-Length is not a number")
        void rejectsWith400OnUnparseableContentLength() {
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/news/archivos/cargar")
                    .header("Content-Length", "not-a-number")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Should NOT invoke the downstream chain when Content-Length is malformed")
        void doesNotInvokeChainOnMalformedContentLength() {
            MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/news/archivos/cargar")
                    .header("Content-Length", "not-a-number")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verify(chain, never()).filter(any(ServerWebExchange.class));
        }
    }

    @Nested
    @DisplayName("Error message formatting")
    class ErrorMessageFormatting {

        @Test
        @DisplayName("Should render sizes using decimal (1000-based) MB, matching the original filter")
        void rendersErrorMessageUsingDecimalMegabytes() {
            // A 5_000_000-byte limit is exactly 5.0 MB in decimal (1000-based) units, but would
            // render as 4.8 MB under binary (1024-based) units — this pins the conversion base to
            // the one the original RequestSizeGatewayFilterFactory used (confirmed against real
            // production evidence: a 31,289,555-byte request against that filter's default limit
            // rendered as "31.3 MB where permissible limit is 5.0 MB").
            GatewayLimitsProperties properties = new GatewayLimitsProperties();
            properties.setMaxRequestSize(DataSize.ofBytes(5_000_000));
            RequestSizeGlobalFilter decimalFilter = new RequestSizeGlobalFilter(properties);

            MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/news/archivos/cargar")
                    .header("Content-Length", "31289555")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(decimalFilter.filter(exchange, chain)).verifyComplete();

            String errorMessage = exchange.getResponse().getHeaders().getFirst("errorMessage");
            assertThat(errorMessage)
                    .contains("Request size is 31.3 MB")
                    .contains("permissible limit is 5.0 MB");
        }
    }

    @Nested
    @DisplayName("Filter ordering")
    class FilterOrdering {

        @Test
        @DisplayName("Should have order HIGHEST_PRECEDENCE so it rejects before body transfer")
        void hasHighestPrecedenceOrder() {
            assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        }
    }
}
