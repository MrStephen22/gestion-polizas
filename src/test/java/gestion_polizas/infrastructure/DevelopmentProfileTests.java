package gestion_polizas.infrastructure;

import gestion_polizas.infrastructure.config.ApiKeyProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DevelopmentProfileTests {
    @LocalServerPort
    int port;

    @Autowired
    ApiKeyProperties properties;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    @Test
    void h2ConsoleIsAccessibleWithoutApiKeyInDevelopment() throws Exception {
        var response = client.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/h2-console/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Frame-Options")).contains("SAMEORIGIN");
        assertThat(response.body()).contains("H2");
    }

    @Test
    void configuredDevelopmentKeyPassesSecurityOnRealServer() throws Exception {
        String base = "http://localhost:" + port + "/does-not-exist";
        var rejected = client.send(HttpRequest.newBuilder(URI.create(base)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(rejected.statusCode()).isEqualTo(401);
        var accepted = client.send(HttpRequest.newBuilder(URI.create(base))
                        .header("x-api-key", properties.getApiKey()).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(accepted.statusCode()).isEqualTo(404);
        assertThat(accepted.body()).contains("\"success\":false", "\"body\":null");
    }
}
