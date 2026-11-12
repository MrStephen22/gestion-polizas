package gestion_polizas.infrastructure.adapters.output.core;

import gestion_polizas.domain.exceptions.CoreNotificationException;
import gestion_polizas.domain.ports.out.CoreNotificationPort;
import gestion_polizas.infrastructure.config.CoreProperties;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;

public class CoreHttpAdapter implements CoreNotificationPort {
    private static final Logger log = LoggerFactory.getLogger(CoreHttpAdapter.class);
    private static final String EVENT = "ACTUALIZACION";
    private static final String FAILURE = "No fue posible notificar al CORE";
    private final RestClient client;

    public CoreHttpAdapter(CoreProperties properties, String apiKey) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.responseTimeout());
        client = RestClient.builder().baseUrl(properties.baseUrl().toString())
                .requestFactory(factory).defaultHeader("x-api-key", apiKey).build();
    }

    @Override
    public void notificarActualizacion(Long polizaId) {
        try {
            CoreReply reply = client.post().uri("/core-mock/evento")
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .body(new CoreEvent(EVENT, polizaId)).retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                        int status = response.getStatusCode().value();
                        throw failure(polizaId, "HTTP_STATUS", status, new CoreHttpStatusException(status));
                    })
                    .body(CoreReply.class);
            if (reply == null || !Boolean.TRUE.equals(reply.success())
                    || reply.message() == null || reply.message().isBlank() || reply.body() == null
                    || !EVENT.equals(reply.body().evento()) || !polizaId.equals(reply.body().polizaId())) {
                throw failure(polizaId, "INVALID_RESPONSE", null,
                        new IllegalStateException("Confirmacion CORE invalida"));
            }
        } catch (RestClientException ex) {
            throw failure(polizaId, category(ex), null, ex);
        }
    }

    private CoreNotificationException failure(Long polizaId, String category, Integer status, Throwable cause) {
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        // Retain original causes for diagnosis, but log no original messages or external payloads.
        var safeTrace = new RuntimeException("Clase tecnica: " + cause.getClass().getName());
        safeTrace.setStackTrace(cause.getStackTrace());
        log.error("Fallo CORE categoria={} polizaId={} status={} causaRaiz={}",
                category, polizaId, status, root.getClass().getName(), safeTrace);
        return new CoreNotificationException(FAILURE, cause);
    }

    private String category(RestClientException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException
                    || cause instanceof TimeoutException) return "TIMEOUT";
            if (cause instanceof ConnectException || cause instanceof UnknownHostException
                    || cause instanceof NoRouteToHostException) return "CONNECTION";
            if (cause.getCause() == cause) break;
        }
        return exception instanceof ResourceAccessException ? "TRANSPORT_ERROR" : "INVALID_RESPONSE";
    }

    private static final class CoreHttpStatusException extends RuntimeException {
        private CoreHttpStatusException(int status) {
            super("Estado HTTP CORE: " + status);
        }
    }

    private record CoreEvent(String evento, Long polizaId) {}
    private record CoreReply(Boolean success, String message, CoreEvent body) {}
}
