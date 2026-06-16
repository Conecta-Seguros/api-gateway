package conectaseguros.co.api.gateway.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configures Caffeine as the Spring Cache provider for the API Gateway.
 *
 * <p>Although the gateway does not persist data, caching is used by the load balancer
 * (Spring Cloud LoadBalancer Caffeine cache) and can be leveraged by custom filters
 * for response caching or token validation caching.
 *
 * <p>Cache settings:
 * <ul>
 *   <li>TTL: 10 minutes — aligned with the platform-wide caching TTL.</li>
 *   <li>Max size: 500 entries — sufficient for a gateway service.</li>
 *   <li>{@code recordStats()} — exposes Caffeine hit/miss/eviction counters to
 *       Micrometer automatically.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(500)
                        .recordStats()
        );
        return manager;
    }
}
