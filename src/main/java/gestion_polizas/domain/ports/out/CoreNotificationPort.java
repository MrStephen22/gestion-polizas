package gestion_polizas.domain.ports.out;

import gestion_polizas.domain.exceptions.CoreNotificationException;

/** Notifies an aggregate update; transport and the mock payload belong to its adapter. */
public interface CoreNotificationPort {
    /** Normal return means success; failure must throw CoreNotificationException. No implicit retries. */
    void notificarActualizacion(Long polizaId) throws CoreNotificationException;
}
