package gestion_polizas.infrastructure.adapters.input.rest.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

@Schema(description = "Intento de actualización enviado al mock CORE")
public record CoreEventRequest(
        @NotBlank @Pattern(regexp = "ACTUALIZACION")
        @Schema(example = "ACTUALIZACION", allowableValues = "ACTUALIZACION") String evento,
        @NotNull @Positive @Schema(example = "555") Long polizaId) {}
