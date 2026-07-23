package conectaseguros.co.api.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global gateway filter that rejects requests whose {@code Content-Length} exceeds the
 * configured {@link GatewayLimitsProperties#getMaxRequestSize()} with HTTP 413.
 *
 * <p>Replaces the {@code RequestSize} gateway filter shortcut
 * ({@code spring.cloud.gateway.server.webflux.default-filters[n]=RequestSize}), whose
 * indexed {@code args.maxSize} value silently failed to bind as a {@code DataSize} in
 * this environment and fell back to the library's hardcoded 5MB default — a request
 * larger than 5MB was rejected with 413 even though the property file declared
 * {@code maxSize=55MB}. Binding the same value as a scalar {@code DataSize} via
 * {@link GatewayLimitsProperties} (backed by Spring Boot's {@code Binder}) is the
 * reliable path, so this filter reads that instead of relying on the shortcut.
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} so oversized requests are rejected
 * before their body is transferred downstream, mirroring the original {@code
 * RequestSizeGatewayFilterFactory} behavior (Content-Length inspection only, no
 * effect if the header is missing).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestSizeGlobalFilter implements GlobalFilter, Ordered {

    private static final String CONTENT_LENGTH_HEADER = "Content-Length";
    private static final String ERROR_MESSAGE_HEADER = "errorMessage";
    private static final String ERROR_MESSAGE_TEMPLATE =
            "Request size is larger than permissible limit."
                    + " Request size is %s where permissible limit is %s";

    private final GatewayLimitsProperties gatewayLimitsProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String contentLength = request.getHeaders().getFirst(CONTENT_LENGTH_HEADER);

        if (!ObjectUtils.isEmpty(contentLength)) {
            long requestSizeBytes = Long.parseLong(contentLength);
            long maxRequestSizeBytes = gatewayLimitsProperties.getMaxRequestSize().toBytes();

            if (requestSizeBytes > maxRequestSizeBytes) {
                return rejectRequest(exchange, requestSizeBytes, maxRequestSizeBytes);
            }
        }

        return chain.filter(exchange);
    }

    private Mono<Void> rejectRequest(
            ServerWebExchange exchange, long requestSizeBytes, long maxRequestSizeBytes) {
        log.warn(
                "Rejecting request to {}: size {} bytes exceeds limit of {} bytes",
                exchange.getRequest().getPath(),
                requestSizeBytes,
                maxRequestSizeBytes);

        exchange.getResponse().setStatusCode(HttpStatus.CONTENT_TOO_LARGE);
        if (!exchange.getResponse().isCommitted()) {
            exchange.getResponse()
                    .getHeaders()
                    .add(
                            ERROR_MESSAGE_HEADER,
                            String.format(
                                    ERROR_MESSAGE_TEMPLATE,
                                    toReadableSize(requestSizeBytes),
                                    toReadableSize(maxRequestSizeBytes)));
        }
        return exchange.getResponse().setComplete();
    }

    private String toReadableSize(long bytes) {
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
