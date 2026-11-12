package gestion_polizas.application.dto;

import gestion_polizas.domain.enums.EstadoRiesgo;
import gestion_polizas.domain.model.Riesgo;

public record RiesgoDto(Long id, EstadoRiesgo estado) {
    public static RiesgoDto from(Riesgo riesgo) {
        return new RiesgoDto(riesgo.getId(), riesgo.getEstado());
    }
}
