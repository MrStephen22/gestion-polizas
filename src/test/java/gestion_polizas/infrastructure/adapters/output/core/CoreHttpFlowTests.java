package gestion_polizas.infrastructure.adapters.output.core;

import gestion_polizas.domain.enums.EstadoPoliza;
import gestion_polizas.domain.enums.EstadoRiesgo;
import gestion_polizas.domain.enums.TipoPoliza;
import gestion_polizas.domain.model.Poliza;
import gestion_polizas.domain.model.Riesgo;
import gestion_polizas.domain.ports.out.CoreNotificationPort;
import gestion_polizas.domain.ports.out.PolizaRepositoryPort;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "app.security.api-key=core-flow-key", "app.core.mode=http", "app.renovacion.ipc-porcentaje=5",
        "app.dev-data.enabled=false", "spring.datasource.url=jdbc:h2:mem:core-flow;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false"
})
@ExtendWith(OutputCaptureExtension.class)
class CoreHttpFlowTests {
    private static final int PORT = availablePort();
    @Autowired PolizaRepositoryPort repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ApplicationContext context;
    private final HttpClient client = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void configureLocalCore(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("app.core.base-url", () -> "http://localhost:" + PORT);
    }

    @BeforeEach
    void seed() {
        jdbc.update("delete from riesgos");
        jdbc.update("delete from polizas");
        repository.guardar(new Poliza(555L, TipoPoliza.COLECTIVA, LocalDate.of(2026, 1, 1), 12,
                new BigDecimal("1000"), List.of(new Riesgo(5551L), new Riesgo(5552L))));
    }

    @Test
    void renovacionRealLlegaAlMockProtegidoYConfirmaPersistencia(CapturedOutput output) throws Exception {
        assertThat(context.getBeansOfType(CoreNotificationPort.class)).hasSize(1);
        assertThat(context.getBean(CoreNotificationPort.class)).isInstanceOf(CoreHttpAdapter.class);
        var response = post("/polizas/555/renovar");
        assertThat(response.statusCode()).isEqualTo(200);
        var json = mapper.readTree(response.body());
        assertThat(json.path("success").asBoolean()).isTrue();
        assertThat(json.path("body").path("estado").asText()).isEqualTo("RENOVADA");
        Poliza persisted = repository.buscarPorId(555L).orElseThrow();
        assertThat(persisted.getCanonMensual()).isEqualTo(new BigDecimal("1050.00"));
        assertThat(persisted.getFechaInicioVigencia()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(output).contains("Intento CORE evento=ACTUALIZACION polizaId=555").doesNotContain("core-flow-key");
    }

    @Test
    void cancelacionRealNotificaUnaSolaVezAunqueSeRepita(CapturedOutput output) throws Exception {
        assertThat(post("/polizas/555/cancelar").statusCode()).isEqualTo(200);
        Long revision = repository.buscarPorId(555L).orElseThrow().getRevision();
        assertThat(post("/polizas/555/cancelar").statusCode()).isEqualTo(200);
        Poliza persisted = repository.buscarPorId(555L).orElseThrow();
        assertThat(persisted.getEstado()).isEqualTo(EstadoPoliza.CANCELADA);
        assertThat(persisted.getRevision()).isEqualTo(revision);
        assertThat(persisted.getRiesgos()).allSatisfy(r -> assertThat(r.getEstado()).isEqualTo(EstadoRiesgo.CANCELADO));
        assertThat(output.getAll().split("Intento CORE evento=ACTUALIZACION polizaId=555", -1).length - 1).isEqualTo(1);
    }

    private HttpResponse<String> post(String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + PORT + path))
                .header("x-api-key", "core-flow-key").POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static int availablePort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
