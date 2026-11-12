package gestion_polizas.infrastructure.adapters.input.rest.controllers;

import gestion_polizas.infrastructure.adapters.input.rest.requests.CoreEventRequest;
import gestion_polizas.infrastructure.adapters.input.rest.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/core-mock", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "CORE mock", description = "Registra intentos; no actualiza un CORE externo")
public class CoreMockController {
    private static final Logger log = LoggerFactory.getLogger(CoreMockController.class);

    @PostMapping(value = "/evento", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Registrar intento de actualización CORE",
            description = "Exige x-api-key. Registra evento e identificador y devuelve su confirmación, sin consultar pólizas.")
    public ResponseEntity<ApiResponse<CoreEventRequest>> evento(@Valid @RequestBody CoreEventRequest request) {
        log.info("Intento CORE evento={} polizaId={}", request.evento(), request.polizaId());
        return ResponseEntity.ok(ApiResponse.success(request));
    }
}
