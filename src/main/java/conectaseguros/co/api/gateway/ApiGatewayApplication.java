package conectaseguros.co.api.gateway;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import java.util.Objects;

@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

	public static void main(String[] args) {

		Dotenv dotenv = Dotenv.configure()
				.directory("./api-gateway")
				.load();

		System.setProperty("SPRING_OAUTH2_CLIENT_SECRET", Objects.requireNonNull(dotenv.get("SPRING_OAUTH2_CLIENT_SECRET")));

		SpringApplication.run(ApiGatewayApplication.class, args);
	}

}
