package gestion_polizas.domain.model;

import gestion_polizas.domain.enums.EstadoRiesgo;
import gestion_polizas.domain.exceptions.InvalidDomainDataException;

/** Immutable member of a Poliza aggregate; state transitions belong to Poliza. */
public final class Riesgo {
    private final Long id;
    private final EstadoRiesgo estado;

    public Riesgo(Long id) {
        this(id, EstadoRiesgo.ACTIVO);
    }

    private Riesgo(Long id, EstadoRiesgo estado) {
        if (id == null || estado == null) {
            throw new InvalidDomainDataException("El identificador y estado del riesgo son obligatorios");
        }
        this.id = id;
        this.estado = estado;
    }

    /** Restores a snapshot without replaying a cancellation. */
    public static Riesgo rehidratar(Long id, EstadoRiesgo estado) {
        return new Riesgo(id, estado);
    }

    Riesgo cancelar() {
        return estado == EstadoRiesgo.CANCELADO ? this : new Riesgo(id, EstadoRiesgo.CANCELADO);
    }

    public Long getId() {
        return id;
    }

    public EstadoRiesgo getEstado() {
        return estado;
    }
}
