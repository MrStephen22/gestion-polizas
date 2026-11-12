package gestion_polizas.infrastructure.adapters.output.core;

import com.sun.net.httpserver.HttpServer;
import gestion_polizas.domain.exceptions.CoreNotificationException;
import gestion_polizas.infrastructure.config.CoreProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClientException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class CoreHttpAdapterTests {
    private static final String OK = """
            {"success":true,"message":"Operacion realizada correctamente",
             "body":{"evento":"ACTUALIZACION","polizaId":555}}
            """;
    private HttpServer server;
    private ExecutorService executor;
    private URI baseUrl;
    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedKey = new AtomicReference<>();
    private final AtomicReference<Reply> reply = new AtomicReference<>(new Reply(200, OK, "application/json", 0));

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/core-mock/evento", exchange -> {
            attempts.incrementAndGet();
            receivedKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Reply response = reply.get();
            try {
                if (response.delayMillis() > 0) Thread.sleep(response.delayMillis());
                exchange.getResponseHeaders().set("Content-Type", response.contentType());
                byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(response.status(), response.status() == 204 ? -1 : bytes.length);
                if (response.status() != 204) exchange.getResponseBody().write(bytes);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (IOException ex) {
                // Expected when a timeout test disconnects before this delayed response is written.
            } finally {
                exchange.close();
            }
        });
        server.start();
        baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void enviarEventoYClaveConJsonCompatibleConElMock() {
        adapter(Duration.ofSeconds(2)).notificarActualizacion(555L);
        var json = JsonMapper.builder().build().readTree(receivedBody.get());
        assertThat(json.path("evento").asText()).isEqualTo("ACTUALIZACION");
        assertThat(json.path("polizaId").asLong()).isEqualTo(555);
        assertThat(receivedKey.get()).isEqualTo("test-core-key");
        assertThat(attempts.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 400, 401, 403, 404, 409, 500, 503})
    void rechazarEstadosNoExitososSinReintentos(int status) {
        reply.set(new Reply(status, "{\"detalle\":\"dato privado\"}", "application/json", 0));
        assertFailure(adapter(Duration.ofSeconds(2)));
        assertThat(attempts.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{", "[]",
            "{\"success\":false,\"message\":\"rechazado\",\"body\":{\"evento\":\"ACTUALIZACION\",\"polizaId\":555}}",
            "{\"success\":true,\"message\":\"ok\",\"body\":null}",
            "{\"success\":true,\"message\":\"\",\"body\":{\"evento\":\"ACTUALIZACION\",\"polizaId\":555}}",
            "{\"success\":true,\"message\":\"ok\",\"body\":{\"evento\":\"OTRO\",\"polizaId\":555}}",
            "{\"success\":true,\"message\":\"ok\",\"body\":{\"evento\":\"ACTUALIZACION\",\"polizaId\":556}}"})
    void rechazarRespuestasInesperadas(String body) {
        reply.set(new Reply(200, body, "application/json", 0));
        assertFailure(adapter(Duration.ofSeconds(2)));
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void rechazarContenidoNoJson() {
        reply.set(new Reply(200, "ok", "text/plain", 0));
        assertFailure(adapter(Duration.ofSeconds(2)));
    }

    @Test
    void respuestaSinConfirmacionNoEsExito() {
        reply.set(new Reply(204, "", "application/json", 0));
        assertFailure(adapter(Duration.ofSeconds(2)));
    }

    @Test
    void timeoutDeRespuestaSeTraduceSinReintentar(CapturedOutput output) {
        reply.set(new Reply(200, OK, "application/json", 2000));
        assertFailure(adapter(Duration.ofMillis(500)));
        assertThat(attempts.get()).isEqualTo(1);
        assertThat(output).contains("categoria=TIMEOUT", "polizaId=555").doesNotContain("test-core-key");
    }

    @Test
    void errorDeConexionSeTraduce(CapturedOutput output) {
        server.stop(0);
        assertFailure(adapter(Duration.ofSeconds(1)));
        assertThat(output).contains("categoria=CONNECTION", "polizaId=555").doesNotContain("test-core-key");
    }

    @Test
    void diagnosticoHttpIncluyeEstadoSinExponerCuerpoOClave(CapturedOutput output) {
        reply.set(new Reply(503, "private-core-body test-core-key", "application/json", 0));
        Throwable failure = catchThrowable(() -> adapter(Duration.ofSeconds(2)).notificarActualizacion(555L));
        assertThat(failure).isInstanceOf(CoreNotificationException.class)
                .hasMessage("No fue posible notificar al CORE").hasCauseInstanceOf(RuntimeException.class);
        assertThat(failure.getCause()).hasMessage("Estado HTTP CORE: 503");
        assertThat(output).contains("categoria=HTTP_STATUS", "polizaId=555", "status=503")
                .doesNotContain("private-core-body", "test-core-key");
    }

    @Test
    void conservarCausaDeDeserializacionSinRegistrarSuContenido(CapturedOutput output) {
        reply.set(new Reply(200, "{\"private-core-body test-core-key\":", "application/json", 0));
        Throwable failure = catchThrowable(() -> adapter(Duration.ofSeconds(2)).notificarActualizacion(555L));
        assertThat(failure).isInstanceOf(CoreNotificationException.class)
                .hasCauseInstanceOf(RestClientException.class);
        assertThat(output).contains("categoria=INVALID_RESPONSE", "polizaId=555", "Clase tecnica:")
                .doesNotContain("private-core-body", "test-core-key");
    }

    private CoreHttpAdapter adapter(Duration timeout) {
        return new CoreHttpAdapter(new CoreProperties(CoreProperties.Mode.HTTP, baseUrl,
                Duration.ofSeconds(1), timeout), "test-core-key");
    }

    private void assertFailure(CoreHttpAdapter adapter) {
        assertThatThrownBy(() -> adapter.notificarActualizacion(555L))
                .isInstanceOf(CoreNotificationException.class)
                .hasMessage("No fue posible notificar al CORE");
    }

    private record Reply(int status, String body, String contentType, long delayMillis) {}
}
