package gestion_polizas.infrastructure.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.core")
public record CoreProperties(@NotNull Mode mode, @NotNull URI baseUrl,
        @NotNull Duration connectTimeout, @NotNull Duration responseTimeout) {
    public enum Mode { FAKE, HTTP }

    @AssertTrue(message = "CORE requiere una URL HTTP(S) absoluta sin credenciales, query ni fragmento")
    public boolean isBaseUrlValid() {
        return baseUrl != null && baseUrl.getHost() != null
                && ("http".equalsIgnoreCase(baseUrl.getScheme()) || "https".equalsIgnoreCase(baseUrl.getScheme()))
                && baseUrl.getUserInfo() == null && baseUrl.getQuery() == null && baseUrl.getFragment() == null;
    }

    @AssertTrue(message = "Los timeouts CORE deben ser positivos")
    public boolean isTimeoutsValid() {
        return connectTimeout != null && responseTimeout != null
                && connectTimeout.compareTo(Duration.ZERO) > 0 && responseTimeout.compareTo(Duration.ZERO) > 0;
    }
}
