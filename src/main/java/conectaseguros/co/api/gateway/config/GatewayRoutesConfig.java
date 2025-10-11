package conectaseguros.co.api.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("batch-processor-service", r -> r
                        .path("/api/v1/batch/**")
                        .uri("lb://batch-processor-service"))

                .route("clients-service", r -> r
                        .path("/api/v1/clients/**")
                        .uri("lb://clients-service"))

                .route("news-service", r -> r
                        .path("/api/v1/news/**")
                        .uri("lb://news-service"))

                .route("payments-service", r -> r
                        .path("/api/v1/payments/**")
                        .uri("lb://payments-service"))

                .route("products-service", r -> r
                        .path("/api/v1/products/**")
                        .uri("lb://products-service"))

                .route("reports-service", r -> r
                        .path("/api/v1/reports/**")
                        .uri("lb://reports-service"))

                .route("discovery-server", r -> r
                        .path("/eureka/web")
                        .filters(f -> f.setPath("/"))
                        .uri("http://conecta-eureka-peer1:8761"))

                .route("discovery-server-static", r -> r
                        .path("/eureka/**")
                        .uri("http://conecta-eureka-peer1:8761"))

                .build();
    }
}