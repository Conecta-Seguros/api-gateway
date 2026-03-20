package conectaseguros.co.api.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import reactor.core.publisher.Mono;

/**
 * WebFlux security configuration for the API Gateway.
 *
 * <p>Security design decisions:
 * <ul>
 *   <li>CSRF disabled: the gateway is a stateless API proxy; clients use JWT Bearer tokens,
 *       not session cookies, so CSRF attacks cannot be mounted.</li>
 *   <li>HSTS intentionally omitted: TLS is terminated at the ingress/load balancer; the
 *       application only sees plain HTTP internally.</li>
 *   <li>Actuator health endpoints are public (required by Kubernetes liveness/readiness
 *       probes). Other actuator endpoints require authentication.</li>
 *   <li>/actuator/info is intentionally protected because it can expose build metadata,
 *       Java runtime version, and OS details that aid fingerprinting.</li>
 *   <li>CORS is handled via Spring Cloud Gateway globalcors properties, not here, to
 *       avoid double CORS processing.</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .headers(headers -> headers
                        .frameOptions(frame ->
                                frame.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
                        .contentTypeOptions(Customizer.withDefaults())
                        .cache(Customizer.withDefaults())
                        .referrerPolicy(referrer ->
                                referrer.policy(ReferrerPolicyServerHttpHeadersWriter
                                        .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(permissions ->
                                permissions.policy("camera=(), microphone=(), geolocation=()"))
                )
                .authorizeExchange(exchanges -> exchanges
                        // Preflight requests
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Kubernetes probes — no auth
                        .pathMatchers("/actuator/health/**").permitAll()
                        .pathMatchers("/actuator/health").permitAll()
                        // Fallback endpoint — no auth (circuit breaker fallback)
                        .pathMatchers("/fallback/**").permitAll()
                        // OAuth2 login flow
                        .pathMatchers("/login/**").permitAll()
                        .pathMatchers("/oauth2/**").permitAll()
                        // Eureka dashboard proxy
                        .pathMatchers("/eureka/**").permitAll()
                        // All actuator endpoints besides health require auth
                        .pathMatchers("/actuator/**").authenticated()
                        // Everything else requires authentication
                        .anyExchange().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authenticationSuccessHandler(
                                new RedirectServerAuthenticationSuccessHandler("/")
                        )
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                )
                .build();
    }

    /**
     * Converts Keycloak JWT {@code realm_access.roles} claim into Spring Security
     * granted authorities with {@code ROLE_} prefix.
     */
    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter =
                new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("realm_access.roles");
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);

        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
    }
}
