package gestion_polizas.infrastructure.adapters.input.rest.controllers;

import gestion_polizas.application.usecases.GestionPolizasService;
import gestion_polizas.domain.enums.EstadoPoliza;
import gestion_polizas.domain.enums.TipoPoliza;
import gestion_polizas.infrastructure.adapters.input.rest.requests.AgregarRiesgoRequest;
import gestion_polizas.infrastructure.adapters.input.rest.responses.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/polizas", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Pólizas", description = "Consulta y modificación de pólizas de arrendamiento")
public class PolizaController {
    private final GestionPolizasService service;

    public PolizaController(GestionPolizasService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar pólizas con filtros y paginación",
            description = "Página inicial 1, tamaño predeterminado 20 y máximo 100. Orden ascendente por ID; conjunto vacío: lastPage=0.")
    public ResponseEntity<ApiResponse<PageResponse<PolizaResponse>>> listar(
            @Parameter(description = "Tipo de póliza; omitir para todos los tipos") @RequestParam(required = false) TipoPoliza tipo,
            @Parameter(description = "Estado; omitir para todos los estados") @RequestParam(required = false) EstadoPoliza estado,
            @Parameter(description = "Número de página, desde 1")
            @RequestParam(defaultValue = "" + PageResponse.FIRST_PAGE) @Min(1) int page,
            @Parameter(description = "Registros por página, de 1 a 100")
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
        var result = service.listar(tipo, estado, page, size);
        int lastPage = Math.toIntExact(result.totalRecords() / size + (result.totalRecords() % size == 0 ? 0 : 1));
        var body = new PageResponse<>(result.data().stream().map(PolizaResponse::from).toList(),
                page, lastPage, result.totalRecords(), page < lastPage);
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @GetMapping("/{id}/riesgos")
    @Operation(summary = "Consultar riesgos de una póliza", description = "Incluye riesgos activos y cancelados.")
    public ResponseEntity<ApiResponse<List<RiesgoResponse>>> consultarRiesgos(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                service.consultarRiesgos(id).stream().map(RiesgoResponse::from).toList()));
    }

    @PostMapping("/{id}/renovar")
    @Operation(summary = "Renovar póliza", description = "Aplica el IPC configurado y abre otro periodo de igual duración; notifica CORE.")
    public ResponseEntity<ApiResponse<PolizaResponse>> renovar(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success(PolizaResponse.from(service.renovar(id))));
    }

    @PostMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar póliza", description = "Cancela también sus riesgos. Las cancelaciones repetidas son idempotentes.")
    public ResponseEntity<ApiResponse<PolizaResponse>> cancelar(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success(PolizaResponse.from(service.cancelar(id))));
    }

    @PostMapping(value = "/{id}/riesgos", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @Operation(summary = "Agregar riesgo a póliza colectiva",
            description = "Recibe un ID positivo explícito. Rechaza pólizas individuales, canceladas y riesgos duplicados.")
    public ResponseEntity<ApiResponse<PolizaResponse>> agregarRiesgo(@PathVariable @Positive Long id,
            @Valid @RequestBody AgregarRiesgoRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                PolizaResponse.from(service.agregarRiesgo(id, request.id()))));
    }
}
