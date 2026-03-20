package conectaseguros.co.api.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CacheConfig}.
 *
 * <p>Tests run WITHOUT Spring context — directly instantiates the config
 * class and verifies the CacheManager bean configuration.
 */
class CacheConfigTest {

    private CacheConfig cacheConfig;

    @BeforeEach
    void setUp() {
        cacheConfig = new CacheConfig();
    }

    @Test
    @DisplayName("cacheManager() should return a CaffeineCacheManager instance")
    void cacheManagerIsCaffeineType() {
        CacheManager manager = cacheConfig.cacheManager();
        assertThat(manager).isInstanceOf(CaffeineCacheManager.class);
    }

    @Test
    @DisplayName("CacheManager should create caches dynamically by name")
    void cacheManagerCreatesCacheByName() {
        CacheManager manager = cacheConfig.cacheManager();
        // CaffeineCacheManager creates caches dynamically on getCache()
        assertThat(manager.getCache("gateway-cache")).isNotNull();
    }

    @Test
    @DisplayName("CacheManager should return the same cache instance for the same name")
    void cacheManagerReturnsSameCacheInstance() {
        CacheManager manager = cacheConfig.cacheManager();
        var cache1 = manager.getCache("gateway-cache");
        var cache2 = manager.getCache("gateway-cache");
        assertThat(cache1).isSameAs(cache2);
    }

    @Test
    @DisplayName("Cache should support put and get operations")
    void cacheSupportsPutAndGet() {
        CacheManager manager = cacheConfig.cacheManager();
        var cache = manager.getCache("gateway-cache");
        assertThat(cache).isNotNull();

        cache.put("test-key", "test-value");
        assertThat(cache.get("test-key", String.class)).isEqualTo("test-value");
    }

    @Test
    @DisplayName("Different cache names should return separate cache instances")
    void differentNamesReturnDifferentCaches() {
        CacheManager manager = cacheConfig.cacheManager();
        var cache1 = manager.getCache("cache-one");
        var cache2 = manager.getCache("cache-two");
        assertThat(cache1).isNotSameAs(cache2);
    }
}
