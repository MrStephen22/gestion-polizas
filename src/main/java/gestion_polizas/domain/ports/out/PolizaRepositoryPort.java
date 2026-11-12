package gestion_polizas.domain.ports.out;

import gestion_polizas.domain.enums.EstadoPoliza;
import gestion_polizas.domain.enums.TipoPoliza;
import gestion_polizas.domain.model.Poliza;
import java.util.List;
import java.util.Optional;

/** Persistence of a complete aggregate, without leaking JPA or Spring Data types. */
public interface PolizaRepositoryPort {
    Optional<Poliza> buscarPorId(Long id);

    Optional<Poliza> buscarPorRiesgoId(Long riesgoId);

    /** Null filters mean no restriction. Results must have stable ordering by policy ID. */
    List<Poliza> listar(TipoPoliza tipo, EstadoPoliza estado);

    /** Zero-based page index, stable ascending order by policy ID. */
    PolizaPage listar(TipoPoliza tipo, EstadoPoliza estado, int pageIndex, int size);

    /**
     * Saves the complete aggregate and returns its new persisted revision.
     * Callers must retain the returned snapshot for subsequent writes.
     * A stale or missing revision on an existing aggregate is a concurrency conflict.
     */
    Poliza guardar(Poliza poliza);
}
