package gestion_polizas.infrastructure.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.renovacion")
public record RenovacionProperties(@NotNull @DecimalMin("0") BigDecimal ipcPorcentaje) {}
