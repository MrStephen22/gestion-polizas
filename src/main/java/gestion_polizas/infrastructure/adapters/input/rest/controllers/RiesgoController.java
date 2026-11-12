package gestion_polizas.infrastructure.adapters.input.rest.controllers;

import gestion_polizas.application.usecases.GestionPolizasService;
import gestion_polizas.infrastructure.adapters.input.rest.responses.ApiResponse;
import gestion_polizas.infrastructure.adapters.input.rest.responses.PolizaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/riesgos", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Riesgos")
public class RiesgoController {
    private final GestionPolizasService service;

    public RiesgoController(GestionPolizasService service) {
        this.service = service;
    }

    @PostMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar riesgo", description = "Conserva su registro y devuelve la póliza propietaria. Operación idempotente.")
    public ResponseEntity<ApiResponse<PolizaResponse>> cancelar(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success(PolizaResponse.from(service.cancelarRiesgo(id))));
    }
}
