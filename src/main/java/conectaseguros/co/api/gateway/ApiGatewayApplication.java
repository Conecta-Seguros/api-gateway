package conectaseguros.co.api.gateway;

import io.github.cdimascio.dotenv.Dotenv;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    static void main(String[] args) {
        // Load .env for dev profile BEFORE Spring Boot starts
        if (isDevProfile()) {
            Dotenv dotenv = Dotenv.configure()
                    .directory("./api-gateway")
                    .ignoreIfMalformed()
                    .ignoreIfMissing()
                    .load();

            // Load all OAuth2 related variables from .env
            setIfPresent("SPRING_OAUTH2_ISSUER_URI", dotenv);
            setIfPresent("SPRING_OAUTH2_CLIENT_ID", dotenv);
            setIfPresent("SPRING_OAUTH2_CLIENT_SECRET", dotenv);
        }

        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    private static boolean isDevProfile() {
        String activeProfile = System.getProperty("spring.profiles.active", "default");
        return "dev".equals(activeProfile) || isLocalEnvironment();
    }

    private static boolean isLocalEnvironment() {
        String env = System.getenv("ENVIRONMENT");
        return env == null || env.equals("local") || env.equals("development");
    }

    private static void setIfPresent(@NotNull String key, @NotNull Dotenv dotenv) {
        String value = dotenv.get(key);
        if (value != null) {
            System.setProperty(key, value);
        }
    }
}