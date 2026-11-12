package gestion_polizas.domain.exceptions;

public class ConcurrentPolizaModificationException extends BusinessConflictException {
    public ConcurrentPolizaModificationException() {
        super("La poliza fue modificada por otra operacion; vuelva a consultarla");
    }

    public ConcurrentPolizaModificationException(Throwable cause) {
        this();
        initCause(cause);
    }
}
