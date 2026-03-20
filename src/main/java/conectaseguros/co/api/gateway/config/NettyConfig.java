package conectaseguros.co.api.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Customizes the embedded Netty server for the reactive API Gateway.
 *
 * <p>Netty is the default embedded server for Spring WebFlux applications. This
 * configuration tunes request parsing limits to accommodate OAuth2 tokens and
 * large authorization headers typical in enterprise environments.
 *
 * <p>Settings:
 * <ul>
 *   <li>{@code maxHeaderSize}: 32 KB — JWT tokens with Keycloak realm roles can
 *       easily exceed the default 8 KB limit.</li>
 *   <li>{@code maxInitialLineLength}: 16 KB — accommodates long URLs with query
 *       parameters in search/filter endpoints.</li>
 * </ul>
 */
@Slf4j
@Configuration
public class NettyConfig {

    @Bean
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyCustomizer() {
        return factory -> factory.addServerCustomizers(server ->
                server.httpRequestDecoder(spec -> spec
                        .maxHeaderSize(32 * 1024)
                        .maxInitialLineLength(16 * 1024)
                )
        );
    }
}
