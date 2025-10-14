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

        String activeProfile = System.getProperty("spring.profiles.active", "default");
        if ("dev".equals(activeProfile) || isLocalEnvironment()) {
            Dotenv dotenv = Dotenv.configure()
                    .directory("./api-gateway")
                    .ignoreIfMissing()
                    .load();

            setIfPresent("SPRING_OAUTH2_CLIENT_SECRET", dotenv);
            setIfPresent("SPRING_OAUTH2_CLIENT_ID", dotenv);
            setIfPresent("SPRING_OAUTH2_ISSUER_URI", dotenv);
        }
        
		SpringApplication.run(ApiGatewayApplication.class, args);
	}

    private static void setIfPresent(String key, @NotNull Dotenv dotenv) {
        String value = dotenv.get(key);
        if (value != null) {
            System.setProperty(key, value);
        }
    }

    private static boolean isLocalEnvironment() {
        String env = System.getenv("ENVIRONMENT");
        return env == null || env.equals("local") || env.equals("development");
    }

}
