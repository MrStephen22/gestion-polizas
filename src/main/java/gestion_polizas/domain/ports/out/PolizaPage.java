package gestion_polizas.domain.ports.out;

import gestion_polizas.domain.model.Poliza;
import java.util.List;

/** Persistence result without Spring Data or HTTP pagination types. */
public record PolizaPage(List<Poliza> data, long totalRecords) {
    public PolizaPage {
        data = List.copyOf(data);
    }
}
