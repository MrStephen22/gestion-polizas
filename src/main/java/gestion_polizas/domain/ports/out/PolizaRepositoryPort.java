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

    /** Saves the policy and its risks together; the application owns the transaction boundary. */
    Poliza guardar(Poliza poliza);
}
