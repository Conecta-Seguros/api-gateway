package conectaseguros.co.api.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Circuit breaker fallback controller for the API Gateway.
 *
 * <p>When a downstream service is unavailable and the circuit breaker opens, Spring
 * Cloud Gateway forwards the request to this controller via
 * {@code forward:/fallback/service-unavailable}.
 *
 * <p>This provides a consistent JSON error response to clients instead of a raw
 * 503/502 with no body, which improves the frontend developer experience and
 * allows proper error handling on the client side.
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping(
            value = "/service-unavailable",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                      RequestMethod.PATCH, RequestMethod.DELETE},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<Map<String, Object>>> serviceUnavailable(ServerWebExchange exchange) {
        String originalUri = exchange.getRequest().getURI().getPath();
        log.warn("Circuit breaker fallback triggered for path: {}", originalUri);

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "message", "The requested service is temporarily unavailable. Please try again later.",
                "timestamp", Instant.now().toString(),
                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "path", originalUri
        )));
    }
}
