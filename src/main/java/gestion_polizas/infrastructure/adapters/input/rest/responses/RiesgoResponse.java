package gestion_polizas.infrastructure.adapters.input.rest.responses;

import gestion_polizas.application.dto.RiesgoDto;
import gestion_polizas.domain.enums.EstadoRiesgo;

public record RiesgoResponse(Long id, EstadoRiesgo estado) {
    public static RiesgoResponse from(RiesgoDto dto) {
        return new RiesgoResponse(dto.id(), dto.estado());
    }
}
