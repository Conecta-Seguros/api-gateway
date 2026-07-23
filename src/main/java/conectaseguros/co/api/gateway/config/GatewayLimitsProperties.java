package conectaseguros.co.api.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * Binds gateway-wide request-limit settings under the {@code app.gateway} prefix.
 *
 * <p>Bound as a scalar {@link DataSize} (not an indexed gateway filter-args shortcut),
 * which is the reliable binding path through Spring Boot's {@code Binder} /
 * {@code ApplicationConversionService}. This sidesteps the suspected
 * {@code String -> DataSize} conversion gap in the gateway's filter-args-map shortcut
 * binding used by {@code spring.cloud.gateway.server.webflux.default-filters[n].args.*}
 * (see {@code RequestSizeGlobalFilter}).
 */
@Component
@ConfigurationProperties(prefix = "app.gateway")
public class GatewayLimitsProperties {

    private DataSize maxRequestSize = DataSize.ofMegabytes(55);

    public DataSize getMaxRequestSize() {
        return maxRequestSize;
    }

    public void setMaxRequestSize(DataSize maxRequestSize) {
        if (maxRequestSize == null || maxRequestSize.toBytes() <= 0) {
            throw new IllegalArgumentException(
                    "app.gateway.max-request-size must be a positive DataSize, got: " + maxRequestSize);
        }
        this.maxRequestSize = maxRequestSize;
    }
}
