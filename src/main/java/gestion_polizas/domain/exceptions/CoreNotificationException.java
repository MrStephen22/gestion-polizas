package gestion_polizas.domain.exceptions;

/** Failure of the outbound notification contract, without transport-specific details. */
public class CoreNotificationException extends RuntimeException {
    public CoreNotificationException(String message) {
        super(message);
    }

    public CoreNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
