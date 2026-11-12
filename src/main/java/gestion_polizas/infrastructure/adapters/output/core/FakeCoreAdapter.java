package gestion_polizas.infrastructure.adapters.output.core;

import gestion_polizas.domain.ports.out.CoreNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Temporary local simulation. Does not contact CORE or claim external delivery. */
public class FakeCoreAdapter implements CoreNotificationPort {
    private static final Logger log = LoggerFactory.getLogger(FakeCoreAdapter.class);

    @Override
    public void notificarActualizacion(Long polizaId) {
        log.info("CORE fake: actualizacion simulada para poliza {} (sin envio HTTP)", polizaId);
    }
}
