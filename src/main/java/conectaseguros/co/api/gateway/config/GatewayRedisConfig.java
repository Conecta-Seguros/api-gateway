package conectaseguros.co.api.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Slf4j
@Configuration
public class GatewayRedisConfig {

    @Bean
    public RedisRateLimiter redisRateLimiter(
            ReactiveStringRedisTemplate redisTemplate,
            RedisScript<List<Long>> redisScript,
            ConfigurationService configurationService) {

        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate, redisScript, configurationService);

        RedisRateLimiter.Config defaultConfig = new RedisRateLimiter.Config()
                .setReplenishRate(50)
                .setBurstCapacity(100)
                .setRequestedTokens(1);

        limiter.getConfig().put("defaultFilters", defaultConfig);

        log.info("RedisRateLimiter default fallback config applied: replenishRate=50, burstCapacity=100, requestedTokens=1");
        return limiter;
    }
}
