package gestion_polizas.domain.ports.out;

/** Notifies an aggregate update; transport and the mock payload belong to its adapter. */
public interface CoreNotificationPort {
    /** A failed notification must propagate to the caller, rather than be silently ignored. */
    void notificarActualizacion(Long polizaId);
}
