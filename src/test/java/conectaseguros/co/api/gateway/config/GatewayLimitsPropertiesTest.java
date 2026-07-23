package conectaseguros.co.api.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * Unit tests for {@link GatewayLimitsProperties}.
 *
 * <p>Tests run WITHOUT Spring context — pure unit tests, matching the style of {@link
 * RequestSizeGlobalFilterTest}.
 */
class GatewayLimitsPropertiesTest {

    @Test
    void defaultsToFiftyFiveMegabytes() {
        GatewayLimitsProperties properties = new GatewayLimitsProperties();

        assertThat(properties.getMaxRequestSize()).isEqualTo(DataSize.ofMegabytes(55));
    }

    @Test
    void acceptsAPositiveDataSize() {
        GatewayLimitsProperties properties = new GatewayLimitsProperties();

        properties.setMaxRequestSize(DataSize.ofMegabytes(10));

        assertThat(properties.getMaxRequestSize()).isEqualTo(DataSize.ofMegabytes(10));
    }

    @Test
    void rejectsNull() {
        GatewayLimitsProperties properties = new GatewayLimitsProperties();

        assertThatIllegalArgumentException().isThrownBy(() -> properties.setMaxRequestSize(null));
    }

    @Test
    void rejectsZero() {
        GatewayLimitsProperties properties = new GatewayLimitsProperties();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.setMaxRequestSize(DataSize.ofBytes(0)));
    }

    @Test
    void rejectsNegativeValues() {
        GatewayLimitsProperties properties = new GatewayLimitsProperties();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.setMaxRequestSize(DataSize.ofMegabytes(-1)));
    }

    @Test
    void aRejectedAssignmentDoesNotOverwriteThePreviousValidValue() {
        GatewayLimitsProperties properties = new GatewayLimitsProperties();
        properties.setMaxRequestSize(DataSize.ofMegabytes(20));

        assertThatIllegalArgumentException().isThrownBy(() -> properties.setMaxRequestSize(null));

        assertThat(properties.getMaxRequestSize()).isEqualTo(DataSize.ofMegabytes(20));
    }
}
