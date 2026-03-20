package conectaseguros.co.api.gateway.config;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global gateway filter that enforces security headers and sanitizes dangerous
 * headers before forwarding requests to downstream services.
 *
 * <p>This filter runs at two points:
 * <ul>
 *   <li><b>Pre-filter (request):</b> strips headers that downstream services should
 *       never trust from external clients:
 *       <ul>
 *         <li>{@code X-Request-Source} / {@code X-Service-Name} — internal service
 *             authentication headers. If an external client sends these, a downstream
 *             service would bypass OAuth2 validation. The gateway MUST strip them.</li>
 *         <li>{@code X-Forwarded-Host} / {@code X-Forwarded-Proto} / {@code X-Forwarded-For}
 *             are NOT stripped because {@code server.forward-headers-strategy=framework}
 *             delegates their handling to Spring's {@code ForwardedHeaderTransformer}.</li>
 *       </ul>
 *   </li>
 *   <li><b>Post-filter (response):</b> adds security headers to every response:
 *       <ul>
 *         <li>{@code X-Content-Type-Options: nosniff} — prevents MIME-type sniffing.</li>
 *         <li>{@code X-XSS-Protection: 0} — disables legacy XSS auditor (modern CSP
 *             is preferred; the auditor itself can introduce vulnerabilities).</li>
 *         <li>{@code Content-Security-Policy: default-src 'self'} — basic CSP baseline.</li>
 *         <li>{@code Strict-Transport-Security} — omitted: TLS is terminated at ingress.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>Order is set to {@link Ordered#HIGHEST_PRECEDENCE} + 1 to run before most other
 * filters but after the metrics filter.
 */
@Slf4j
@Component
public class SecurityHeadersGlobalFilter implements GlobalFilter, Ordered {

    private static final String CSP_POLICY = "default-src 'self'; frame-ancestors 'none'";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // --- PRE: sanitize dangerous headers from external requests ---
        ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-Request-Source");
                    headers.remove("X-Service-Name");
                })
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(sanitizedRequest)
                .build();

        return chain.filter(mutatedExchange).then(Mono.fromRunnable(() -> {
            // --- POST: add security headers to response ---
            ServerHttpResponse response = mutatedExchange.getResponse();
            response.getHeaders().putIfAbsent("X-Content-Type-Options", List.of("nosniff"));
            response.getHeaders().putIfAbsent("X-XSS-Protection", List.of("0"));
            response.getHeaders().putIfAbsent("Content-Security-Policy", List.of(CSP_POLICY));
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
