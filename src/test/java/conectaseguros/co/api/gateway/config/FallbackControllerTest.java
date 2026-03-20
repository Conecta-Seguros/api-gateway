package conectaseguros.co.api.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Unit tests for {@link FallbackController}.
 *
 * <p>Uses {@code WebTestClient.bindToController()} — NO Spring context required.
 * This is a pure unit test that directly binds the controller and exercises
 * all HTTP methods supported by the fallback endpoint.
 */
class FallbackControllerTest {

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(new FallbackController())
                .build();
    }

    @Test
    @DisplayName("POST /fallback/service-unavailable returns 200 with 503-status JSON body")
    void postReturnsFallbackResponse() {
        webTestClient.post().uri("/fallback/service-unavailable")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.message").isEqualTo(
                        "The requested service is temporarily unavailable. Please try again later.")
                .jsonPath("$.timestamp").isNotEmpty()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.path").isEqualTo("/fallback/service-unavailable");
    }

    @Test
    @DisplayName("PUT /fallback/service-unavailable returns fallback JSON body")
    void putReturnsFallbackResponse() {
        webTestClient.put().uri("/fallback/service-unavailable")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.path").isEqualTo("/fallback/service-unavailable");
    }

    @Test
    @DisplayName("PATCH /fallback/service-unavailable returns fallback JSON body")
    void patchReturnsFallbackResponse() {
        webTestClient.patch().uri("/fallback/service-unavailable")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.path").isEqualTo("/fallback/service-unavailable");
    }

    @Test
    @DisplayName("DELETE /fallback/service-unavailable returns fallback JSON body")
    void deleteReturnsFallbackResponse() {
        webTestClient.delete().uri("/fallback/service-unavailable")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.path").isEqualTo("/fallback/service-unavailable");
    }

    @Test
    @DisplayName("GET /fallback/service-unavailable returns fallback JSON body")
    void getReturnsFallbackResponse() {
        webTestClient.get().uri("/fallback/service-unavailable")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.message").isEqualTo(
                        "The requested service is temporarily unavailable. Please try again later.")
                .jsonPath("$.timestamp").isNotEmpty()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.path").isEqualTo("/fallback/service-unavailable");
    }

    @Test
    @DisplayName("Response body contains all required fields with correct types")
    void responseBodyStructure() {
        webTestClient.get().uri("/fallback/service-unavailable")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.timestamp").isNotEmpty()
                .jsonPath("$.status").isNumber()
                .jsonPath("$.path").isNotEmpty();
    }
}
