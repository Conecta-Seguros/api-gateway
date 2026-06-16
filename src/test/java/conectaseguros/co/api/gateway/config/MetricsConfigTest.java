package conectaseguros.co.api.gateway.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MetricsConfig}.
 *
 * <p>Tests run WITHOUT Spring context — directly instantiates the config
 * class with mock values and verifies the MeterRegistryCustomizer behavior.
 */
class MetricsConfigTest {

    private final MetricsConfig metricsConfig = new MetricsConfig();

    @Test
    @DisplayName("Should apply 'application' tag with the configured app name")
    void appliesApplicationTag() {
        MockEnvironment env = new MockEnvironment();
        MeterRegistryCustomizer<MeterRegistry> customizer =
                metricsConfig.commonTags("api-gateway", env);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        customizer.customize(registry);

        // Register a dummy counter to verify tags are applied
        registry.counter("test.counter").increment();

        assertThat(registry.find("test.counter").counter())
                .isNotNull()
                .satisfies(counter ->
                        assertThat(counter.getId().getTag("application"))
                                .isEqualTo("api-gateway")
                );
    }

    @Test
    @DisplayName("Should apply 'environment' tag with active profiles")
    void appliesEnvironmentTagWithActiveProfiles() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", "local");

        MeterRegistryCustomizer<MeterRegistry> customizer =
                metricsConfig.commonTags("api-gateway", env);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        customizer.customize(registry);

        registry.counter("test.counter").increment();

        assertThat(registry.find("test.counter").counter())
                .isNotNull()
                .satisfies(counter ->
                        assertThat(counter.getId().getTag("environment"))
                                .isEqualTo("dev,local")
                );
    }

    @Test
    @DisplayName("Should fallback to 'default' when no profiles are active")
    void fallsBackToDefaultWhenNoProfiles() {
        MockEnvironment env = new MockEnvironment();
        // No active profiles set

        MeterRegistryCustomizer<MeterRegistry> customizer =
                metricsConfig.commonTags("api-gateway", env);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        customizer.customize(registry);

        registry.counter("test.counter").increment();

        assertThat(registry.find("test.counter").counter())
                .isNotNull()
                .satisfies(counter ->
                        assertThat(counter.getId().getTag("environment"))
                                .isEqualTo("default")
                );
    }

    @Test
    @DisplayName("Should apply both 'application' and 'environment' tags together")
    void appliesBothTagsTogether() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("k8s");

        MeterRegistryCustomizer<MeterRegistry> customizer =
                metricsConfig.commonTags("api-gateway", env);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        customizer.customize(registry);

        registry.counter("test.counter").increment();

        var counter = registry.find("test.counter").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.getId().getTag("application")).isEqualTo("api-gateway");
        assertThat(counter.getId().getTag("environment")).isEqualTo("k8s");
    }
}
