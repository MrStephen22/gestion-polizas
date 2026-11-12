package gestion_polizas.infrastructure.adapters.input.rest.responses;

import gestion_polizas.application.dto.PolizaDto;
import gestion_polizas.domain.enums.EstadoPoliza;
import gestion_polizas.domain.enums.TipoPoliza;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PolizaResponse(Long id, TipoPoliza tipo, EstadoPoliza estado, BigDecimal canonMensual,
        BigDecimal prima, LocalDate fechaInicioVigencia, LocalDate fechaFinVigencia,
        int mesesVigenciaInicial, List<RiesgoResponse> riesgos) {
    public PolizaResponse {
        riesgos = List.copyOf(riesgos);
    }

    public static PolizaResponse from(PolizaDto dto) {
        return new PolizaResponse(dto.id(), dto.tipo(), dto.estado(), dto.canonMensual(), dto.prima(),
                dto.fechaInicioVigencia(), dto.fechaFinVigencia(), dto.mesesVigenciaInicial(),
                dto.riesgos().stream().map(RiesgoResponse::from).toList());
    }
}
