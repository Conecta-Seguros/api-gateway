package conectaseguros.co.api.gateway.config;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads environment variables from a {@code .env} file at application startup.
 *
 * <p>Bridges local development (where sensitive values live in a {@code .env} file) and
 * production (where they are injected as OS-level environment variables by Docker or
 * Kubernetes). In both cases the variables are available to Spring's {@code ${...}}
 * placeholder resolver without additional wiring.
 *
 * <p>Behaviour by environment:
 * <ul>
 *   <li><b>Local development:</b> reads {@code .env} from the working directory and
 *       promotes each entry to a Java system property.</li>
 *   <li><b>Docker / Kubernetes:</b> the {@code .env} file is absent or empty
 *       ({@code ignoreIfMissing}). OS-level environment variables are already visible
 *       to Spring Boot through {@code SystemEnvironmentPropertySource}.</li>
 * </ul>
 *
 * <p><b>Security:</b> the {@code .env} file is listed in {@code .gitignore} and must
 * never be committed to version control.
 */
@Slf4j
@Configuration
public class DotenvConfig {

    @Bean
    public Dotenv dotenv() {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .ignoreIfMalformed()
                .systemProperties()
                .load();
        log.debug("Dotenv loaded — .env variables promoted to system properties");
        return dotenv;
    }
}
