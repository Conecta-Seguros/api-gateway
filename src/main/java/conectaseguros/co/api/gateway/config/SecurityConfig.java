package conectaseguros.co.api.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

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
 *   <li>CORS is registered at the Spring Security level so that error responses (403, 401)
 *       also carry the required headers. The gateway globalcors properties handle route-level
 *       CORS; DedupeResponseHeader removes any duplicates on successful responses.</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            GatewayAccessDeniedHandler accessDeniedHandler) {
        return http
                .cors(Customizer.withDefaults())
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
                        // Eureka dashboard proxy — requires authentication
                        .pathMatchers("/eureka/**").authenticated()
                        // All actuator endpoints besides health require auth
                        .pathMatchers("/actuator/**").authenticated()
                        // Mutating operations require ADMIN or CARTERA — CONSULTANT is read-only
                        .pathMatchers(HttpMethod.POST, "/api/**").hasAnyRole("ADMIN", "CARTERA")
                        .pathMatchers(HttpMethod.PUT, "/api/**").hasAnyRole("ADMIN", "CARTERA")
                        .pathMatchers(HttpMethod.PATCH, "/api/**").hasAnyRole("ADMIN", "CARTERA")
                        .pathMatchers(HttpMethod.DELETE, "/api/**").hasAnyRole("ADMIN", "CARTERA")
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
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "Origin",
                "X-Requested-With", "Access-Control-Request-Method", "Access-Control-Request-Headers"
        ));
        config.setExposedHeaders(List.of(
                "Authorization", "Content-Type", "Content-Disposition",
                "X-Total-Count", "X-Page-Number", "X-Page-Size"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Converts Keycloak JWT {@code realm_access.roles} claim into Spring Security
     * granted authorities with {@code ROLE_} prefix.
     */
    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess == null) {
                return List.of();
            }
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.get("roles");
            if (roles == null) {
                return List.of();
            }
            return roles.stream()
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
        });
        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
    }
}
