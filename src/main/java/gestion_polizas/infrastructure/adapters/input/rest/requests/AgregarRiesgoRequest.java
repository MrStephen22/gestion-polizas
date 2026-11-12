package gestion_polizas.infrastructure.adapters.input.rest.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.media.Schema;

public record AgregarRiesgoRequest(@NotNull @Positive @Schema(description = "ID global del nuevo riesgo", example = "501") Long id) {}
