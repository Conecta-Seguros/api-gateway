package conectaseguros.co.api.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;

/**
 * Proves the spec's "Consistent Effective Limit Across Environments" requirement
 * (sdd spec obs #316) at the REAL Spring Boot config-binding level, per-deployment-profile —
 * not by static grep/rg absence-of-override checks, which was tasks.md 4.1's original
 * acceptance criterion and was flagged by sdd-verify (obs #327) as "static evidence only,
 * no runtime/integration test... this scenario is technically UNTESTED at the integration
 * level."
 *
 * <p>Uses {@link ConfigDataEnvironmentPostProcessor#applyTo(org.springframework.core.env.ConfigurableEnvironment)}
 * — the same config-loading machinery {@code @SpringBootTest} / {@code SpringApplication}
 * use internally (invoked here directly since Spring Boot 4 removed the test-only
 * {@code ConfigDataApplicationContextInitializer} convenience class) — to load the REAL
 * {@code application.properties} + {@code application-{profile}.properties} files from the
 * classpath for each of the two real deployment profiles ({@code k8s}, {@code k8s-ha}), then
 * binds {@link GatewayLimitsProperties} via {@code @EnableConfigurationProperties}: the
 * actual Spring Boot {@code Binder}, not a hand-constructed object.
 *
 * <p>This intentionally does NOT boot the full gateway context (routes, Eureka, Keycloak),
 * because doing so would require simulating K8s-only environment variables
 * ({@code EUREKA_USERNAME}, {@code KEYCLOAK_CLIENT_ID}, {@code REDIS_HOST}, ...) that have
 * no defaults in {@code application-k8s.properties} / {@code application-k8s-ha.properties}
 * and are irrelevant to what this test asserts — only the {@code app.gateway.max-request-size}
 * key path (declared once, in the base {@code application.properties}, with no per-profile
 * override) is exercised. {@link RequestSizeGlobalFilterEndToEndTest} separately proves the
 * end-to-end HTTP behavior for the {@code test} profile.
 */
class GatewayLimitsPropertiesProfileBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> ConfigDataEnvironmentPostProcessor.applyTo(context.getEnvironment()))
            .withUserConfiguration(GatewayLimitsPropertiesTestConfig.class);

    @ParameterizedTest(name = "profile=\"{0}\" resolves app.gateway.max-request-size to 55MB")
    @ValueSource(strings = {"k8s", "k8s-ha"})
    @DisplayName("Effective request-size limit is 55MB under both real deployment profiles")
    void resolvesToFiftyFiveMbForDeploymentProfile(String profile) {
        contextRunner
                .withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    GatewayLimitsProperties properties = context.getBean(GatewayLimitsProperties.class);
                    assertThat(properties.getMaxRequestSize()).isEqualTo(DataSize.ofMegabytes(55));
                });
    }

    @EnableConfigurationProperties(GatewayLimitsProperties.class)
    static class GatewayLimitsPropertiesTestConfig {
    }
}
