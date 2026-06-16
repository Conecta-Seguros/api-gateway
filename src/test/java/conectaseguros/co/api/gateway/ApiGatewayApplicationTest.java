package conectaseguros.co.api.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration test — verifies the Spring application context loads correctly.
 *
 * <p>Disables external service dependencies (Eureka, Gateway routing, OAuth2 JWT)
 * via the test profile and property overrides. The {@link ReactiveJwtDecoder} is
 * mocked to prevent the auto-configuration from attempting to fetch JWK keys.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.gateway.enabled=false",
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.main.web-application-type=reactive",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379"
        }
)
@ActiveProfiles("test")
class ApiGatewayApplicationTest {

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @MockitoBean
    private ReactiveClientRegistrationRepository clientRegistrationRepository;

    @Test
    @DisplayName("Spring application context loads successfully")
    void contextLoads() {
        // If this test passes, the application context started without errors.
        // All beans were created and wired correctly.
    }
}
