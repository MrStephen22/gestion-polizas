package gestion_polizas.application.dto;

import java.util.List;

public record ListadoPolizasDto(List<PolizaDto> data, long totalRecords) {
    public ListadoPolizasDto {
        data = List.copyOf(data);
    }
}
