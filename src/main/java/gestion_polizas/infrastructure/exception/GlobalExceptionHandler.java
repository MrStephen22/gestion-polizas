package gestion_polizas.infrastructure.exception;

import gestion_polizas.domain.exceptions.BusinessConflictException;
import gestion_polizas.domain.exceptions.ResourceNotFoundException;
import gestion_polizas.infrastructure.adapters.input.rest.responses.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Preserve Spring MVC's status codes for malformed JSON, invalid parameters,
    // unsupported methods and media types, and missing resources.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            org.springframework.http.HttpHeaders headers,
            org.springframework.http.HttpStatusCode status,
            org.springframework.web.context.request.WebRequest request) {
        String message;
        if (ex instanceof MethodArgumentNotValidException
                || ex instanceof HandlerMethodValidationException) {
            message = "Los datos de la solicitud no son validos";
        } else if (ex instanceof NoResourceFoundException || ex instanceof NoHandlerFoundException) {
            message = "Recurso no encontrado";
        } else if (status.is5xxServerError()) {
            log.error("Error interno al procesar solicitud HTTP", ex);
            message = "Error interno del servidor";
        } else {
            message = "Solicitud invalida";
        }
        return new ResponseEntity<>(ApiResponse.error(message), headers, status);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> validation(ConstraintViolationException ex) {
        return error(HttpStatus.BAD_REQUEST, "Los datos de la solicitud no son validos");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> notFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Recurso no encontrado");
    }

    @ExceptionHandler(BusinessConflictException.class)
    ResponseEntity<ApiResponse<Void>> conflict(BusinessConflictException ex) {
        return error(HttpStatus.CONFLICT, "La operacion presenta un conflicto de negocio");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
        log.error("Error inesperado al procesar solicitud HTTP", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(message));
    }
}
