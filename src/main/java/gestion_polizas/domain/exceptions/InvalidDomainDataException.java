package gestion_polizas.domain.exceptions;

public class InvalidDomainDataException extends RuntimeException {
    public InvalidDomainDataException(String message) {
        super(message);
    }
}
