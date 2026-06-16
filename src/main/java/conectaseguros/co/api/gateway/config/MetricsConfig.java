package conectaseguros.co.api.gateway.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Applies Micrometer common tags to every meter registered in this application.
 *
 * <p>Common tags ensure all metrics exported to Prometheus carry consistent labels,
 * enabling Grafana dashboards to filter and aggregate by service name and active
 * environment without modifying individual metric definitions.
 *
 * <p>Tags applied:
 * <ul>
 *   <li>{@code application} — Spring application name (e.g. {@code api-gateway}).</li>
 *   <li>{@code environment} — comma-joined active Spring profiles (e.g. {@code dev},
 *       {@code k8s}). Falls back to {@code default} when no profile is active.</li>
 * </ul>
 */
@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags(
            @Value("${spring.application.name}") String appName,
            Environment environment) {
        String[] active = environment.getActiveProfiles();
        String profiles = active.length > 0 ? String.join(",", active) : "default";
        return registry -> registry.config()
                .commonTags(
                        "application", appName,
                        "environment", profiles
                );
    }
}
