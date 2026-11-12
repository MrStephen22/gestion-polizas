package gestion_polizas.application.dto;

import gestion_polizas.domain.enums.EstadoPoliza;
import gestion_polizas.domain.enums.TipoPoliza;
import gestion_polizas.domain.model.Poliza;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PolizaDto(Long id, TipoPoliza tipo, EstadoPoliza estado, BigDecimal canonMensual,
        BigDecimal prima, LocalDate fechaInicioVigencia, LocalDate fechaFinVigencia,
        int mesesVigenciaInicial, List<RiesgoDto> riesgos, Long revision) {
    public PolizaDto {
        riesgos = List.copyOf(riesgos);
    }

    public static PolizaDto from(Poliza poliza) {
        return new PolizaDto(poliza.getId(), poliza.getTipo(), poliza.getEstado(), poliza.getCanonMensual(),
                poliza.getPrima(), poliza.getFechaInicioVigencia(), poliza.getFechaFinVigencia(),
                poliza.getMesesVigenciaInicial(), poliza.getRiesgos().stream().map(RiesgoDto::from).toList(),
                poliza.getRevision());
    }
}
