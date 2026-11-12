package gestion_polizas.infrastructure.adapters.input.rest;

import gestion_polizas.application.dto.PolizaDto;
import gestion_polizas.domain.enums.EstadoPoliza;
import gestion_polizas.domain.enums.TipoPoliza;
import gestion_polizas.domain.exceptions.CoreNotificationException;
import gestion_polizas.domain.model.Poliza;
import gestion_polizas.domain.model.Riesgo;
import gestion_polizas.domain.ports.out.CoreNotificationPort;
import gestion_polizas.domain.ports.out.PolizaRepositoryPort;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.security.api-key=123456",
        "app.renovacion.ipc-porcentaje=5",
        "app.dev-data.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:rest-contracts;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import(PolizaRestTests.CoreConfig.class)
class PolizaRestTests {
    @Autowired MockMvc mvc;
    @Autowired PolizaRepositoryPort repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestCore core;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired ApplicationContext context;

    @BeforeEach
    void seed() {
        jdbc.update("delete from riesgos");
        jdbc.update("delete from polizas");
        Poliza cancelled = poliza(4L, TipoPoliza.COLECTIVA, "3000", 401L, 402L);
        cancelled.cancelar();
        Poliza renewed = poliza(3L, TipoPoliza.INDIVIDUAL, "1500", 301L);
        renewed.renovar(new BigDecimal("5"));
        repository.guardar(cancelled);
        repository.guardar(renewed);
        repository.guardar(poliza(2L, TipoPoliza.COLECTIVA, "2000", 201L, 202L, 203L));
        repository.guardar(poliza(1L, TipoPoliza.INDIVIDUAL, "1000", 101L));
        core.events.clear();
        core.fail = false;
    }

    @Test
    void listadoConContratoPaginaPredeterminadaYOrdenEstable() throws Exception {
        success(get("/polizas"), 200)
                .andExpect(jsonPath("$.body.data.length()").value(4))
                .andExpect(jsonPath("$.body.data[0].id").value(1))
                .andExpect(jsonPath("$.body.data[3].id").value(4))
                .andExpect(jsonPath("$.body.currentPage").value(1))
                .andExpect(jsonPath("$.body.lastPage").value(1))
                .andExpect(jsonPath("$.body.totalRecords").value(4))
                .andExpect(jsonPath("$.body.hasMorePages").value(false))
                .andExpect(jsonPath("$.body.data[0].revision").doesNotExist())
                .andExpect(jsonPath("$.body.data[0].fechaInicioVigencia").value("2026-01-01"))
                .andExpect(jsonPath("$.body.data[0].prima").value(12000.00));
    }

    @Test
    void filtrosOpcionalesYCombinados() throws Exception {
        success(get("/polizas").param("tipo", "COLECTIVA"), 200)
                .andExpect(jsonPath("$.body.data.length()").value(2))
                .andExpect(jsonPath("$.body.totalRecords").value(2));
        success(get("/polizas").param("estado", "RENOVADA"), 200)
                .andExpect(jsonPath("$.body.data[0].id").value(3));
        success(get("/polizas").param("tipo", "COLECTIVA").param("estado", "ACTIVA"), 200)
                .andExpect(jsonPath("$.body.data.length()").value(1))
                .andExpect(jsonPath("$.body.data[0].id").value(2))
                .andExpect(jsonPath("$.body.data[0].riesgos.length()").value(3));
    }

    @Test
    void paginacionConLimitesYUltimaPagina() throws Exception {
        success(get("/polizas").param("size", "1"), 200)
                .andExpect(jsonPath("$.body.data.length()").value(1))
                .andExpect(jsonPath("$.body.lastPage").value(4));
        success(get("/polizas").param("size", "100"), 200)
                .andExpect(jsonPath("$.body.data.length()").value(4));
        success(get("/polizas").param("size", "2"), 200)
                .andExpect(jsonPath("$.body.data[0].id").value(1))
                .andExpect(jsonPath("$.body.data[1].id").value(2))
                .andExpect(jsonPath("$.body.lastPage").value(2))
                .andExpect(jsonPath("$.body.hasMorePages").value(true));
        success(get("/polizas").param("page", "2").param("size", "2"), 200)
                .andExpect(jsonPath("$.body.data[0].id").value(3))
                .andExpect(jsonPath("$.body.data[1].id").value(4))
                .andExpect(jsonPath("$.body.hasMorePages").value(false));
        success(get("/polizas").param("page", "3").param("size", "2"), 200)
                .andExpect(jsonPath("$.body.data").isEmpty())
                .andExpect(jsonPath("$.body.currentPage").value(3))
                .andExpect(jsonPath("$.body.lastPage").value(2))
                .andExpect(jsonPath("$.body.totalRecords").value(4))
                .andExpect(jsonPath("$.body.hasMorePages").value(false));
        success(get("/polizas").param("page", "2147483647").param("size", "100"), 200)
                .andExpect(jsonPath("$.body.data").isEmpty());
    }

    @Test
    void tamanoPredeterminadoEsVeinte() throws Exception {
        for (long id = 5; id <= 21; id++) {
            repository.guardar(poliza(id, TipoPoliza.INDIVIDUAL, "100", 1000 + id));
        }
        success(get("/polizas"), 200)
                .andExpect(jsonPath("$.body.data.length()").value(20))
                .andExpect(jsonPath("$.body.lastPage").value(2))
                .andExpect(jsonPath("$.body.totalRecords").value(21))
                .andExpect(jsonPath("$.body.hasMorePages").value(true));
        success(get("/polizas").param("page", "2"), 200)
                .andExpect(jsonPath("$.body.data.length()").value(1))
                .andExpect(jsonPath("$.body.data[0].id").value(21));
    }

    @Test
    void filtrosSinResultadosDevuelvenUltimaPaginaCero() throws Exception {
        success(get("/polizas").param("tipo", "COLECTIVA").param("estado", "RENOVADA"), 200)
                .andExpect(jsonPath("$.body.data").isEmpty())
                .andExpect(jsonPath("$.body.currentPage").value(1))
                .andExpect(jsonPath("$.body.lastPage").value(0))
                .andExpect(jsonPath("$.body.totalRecords").value(0))
                .andExpect(jsonPath("$.body.hasMorePages").value(false));
    }

    @Test
    void consultasNoModificanDatosNiNotificanYPaginaNoProduceNMasUno() throws Exception {
        var before = snapshot();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        success(get("/polizas").param("size", "2"), 200);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        success(get("/polizas/2/riesgos"), 200)
                .andExpect(jsonPath("$.body.length()").value(3))
                .andExpect(jsonPath("$.body[0].id").value(201))
                .andExpect(jsonPath("$.body[0].estado").value("ACTIVO"));
        assertThat(snapshot()).isEqualTo(before);
        assertThat(core.events).isEmpty();
        assertThat(context.containsBean("developmentDataInitializer")).isFalse();
    }

    @Test
    void renovarPersisteCanonPrimaFechasYNotifica() throws Exception {
        success(post("/polizas/1/renovar"), 200)
                .andExpect(jsonPath("$.body.estado").value("RENOVADA"))
                .andExpect(jsonPath("$.body.canonMensual").value(1050.00))
                .andExpect(jsonPath("$.body.prima").value(12600.00))
                .andExpect(jsonPath("$.body.fechaInicioVigencia").value("2027-01-01"))
                .andExpect(jsonPath("$.body.fechaFinVigencia").value("2028-01-01"));
        assertThat(repository.buscarPorId(1L).orElseThrow().getCanonMensual())
                .isEqualTo(new BigDecimal("1050.00"));
        assertThat(core.events).containsExactly(1L);
    }

    @Test
    void cancelarPolizaEsIdempotenteYPersisteTodosLosRiesgos() throws Exception {
        String first = success(post("/polizas/2/cancelar"), 200)
                .andExpect(jsonPath("$.body.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.body.riesgos[0].estado").value("CANCELADO"))
                .andReturn().getResponse().getContentAsString();
        Long version = repository.buscarPorId(2L).orElseThrow().getRevision();
        success(post("/polizas/2/cancelar"), 200).andExpect(content().json(first));
        assertThat(repository.buscarPorId(2L).orElseThrow().getRevision()).isEqualTo(version);
        assertThat(jdbc.queryForObject("select count(*) from riesgos where poliza_id=2 and estado='CANCELADO'",
                Long.class)).isEqualTo(3);
        assertThat(core.events).containsExactly(2L);
    }

    @Test
    void agregarRiesgoColectivoRetorna201YPersisteRelacion() throws Exception {
        success(post("/polizas/2/riesgos").contentType(MediaType.APPLICATION_JSON).content("{\"id\":501}"), 201)
                .andExpect(jsonPath("$.body.riesgos.length()").value(4))
                .andExpect(jsonPath("$.body.riesgos[3].id").value(501))
                .andExpect(jsonPath("$.body.riesgos[3].estado").value("ACTIVO"));
        assertThat(repository.buscarPorRiesgoId(501L).orElseThrow().getId()).isEqualTo(2L);
        assertThat(core.events).containsExactly(2L);
    }

    @Test
    void cancelarRiesgoEsIdempotenteYConservaRegistro() throws Exception {
        String first = success(post("/riesgos/201/cancelar"), 200)
                .andExpect(jsonPath("$.body.riesgos[0].estado").value("CANCELADO"))
                .andExpect(jsonPath("$.body.riesgos[1].estado").value("ACTIVO"))
                .andReturn().getResponse().getContentAsString();
        Long version = repository.buscarPorId(2L).orElseThrow().getRevision();
        success(post("/riesgos/201/cancelar"), 200).andExpect(content().json(first));
        assertThat(repository.buscarPorId(2L).orElseThrow().getRevision()).isEqualTo(version);
        success(get("/polizas/2/riesgos"), 200)
                .andExpect(jsonPath("$.body.length()").value(3))
                .andExpect(jsonPath("$.body[0].estado").value("CANCELADO"));
        assertThat(core.events).containsExactly(2L);
    }

    @ParameterizedTest
    @MethodSource("businessRequests")
    void endpointsRechazanClaveAusente(String method, String path) throws Exception {
        error(request(method, path), false, 401);
        assertThat(core.events).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("businessRequests")
    void endpointsRechazanClaveIncorrecta(String method, String path) throws Exception {
        error(request(method, path).header("x-api-key", "incorrecta"), false, 401);
        assertThat(core.events).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=0", "page=-1", "page=abc", "page=2147483648", "size=0", "size=-1",
            "size=101", "size=abc", "tipo=OTRA", "tipo=colectiva", "estado=VENCIDA"})
    void parametrosInvalidosRetornan400(String query) throws Exception {
        String[] parameter = query.split("=");
        error(get("/polizas").param(parameter[0], parameter[1]), true, 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"id\":null}", "{\"id\":0}", "{\"id\":-1}",
            "{\"id\":\"abc\"}", "{\"id\":501.5}", "{\"id\":9223372036854775808}", "{", "null", ""})
    void altaValidaRequestAntesDeModificar(String body) throws Exception {
        var before = snapshot();
        error(post("/polizas/2/riesgos").contentType(MediaType.APPLICATION_JSON).content(body), true, 400);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(core.events).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void identificadoresInvalidosRetornan400(String id) throws Exception {
        error(get("/polizas/" + id + "/riesgos"), true, 400);
        error(post("/polizas/" + id + "/renovar"), true, 400);
        error(post("/polizas/" + id + "/cancelar"), true, 400);
        error(post("/polizas/" + id + "/riesgos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":501}"), true, 400);
        error(post("/riesgos/" + id + "/cancelar"), true, 400);
    }

    @Test
    void recursosInexistentesRetornan404() throws Exception {
        error(get("/polizas/999/riesgos"), true, 404);
        error(post("/polizas/999/renovar"), true, 404);
        error(post("/polizas/999/cancelar"), true, 404);
        error(post("/polizas/999/riesgos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":501}"), true, 404);
        error(post("/riesgos/999/cancelar"), true, 404);
        assertThat(core.events).isEmpty();
    }

    @Test
    void conflictosDeNegocioRetornan409SinCambios() throws Exception {
        var before = snapshot();
        error(post("/polizas/4/renovar"), true, 409);
        error(post("/polizas/4/riesgos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":501}"), true, 409);
        error(post("/polizas/1/riesgos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":501}"), true, 409);
        error(post("/polizas/2/riesgos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":201}"), true, 409);
        error(post("/polizas/2/riesgos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":101}"), true, 409);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(core.events).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/polizas/2/renovar", "/polizas/2/cancelar", "/polizas/2/riesgos", "/riesgos/201/cancelar"})
    void falloCoreRetorna502SinDetallesYReviertePersistencia(String path, CapturedOutput output) throws Exception {
        var before = snapshot();
        core.fail = true;
        error(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"id\":501}"), true, 502)
                .andExpect(jsonPath("$.message").value("No fue posible notificar al CORE"));
        assertThat(snapshot()).isEqualTo(before);
        assertThat(core.events).containsExactly(2L);
        assertThat(output).doesNotContain("private-core-body", "sensitive-test-token");
    }

    private ResultActions success(MockHttpServletRequestBuilder request, int status) throws Exception {
        return mvc.perform(request.header("x-api-key", "123456"))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Operacion realizada correctamente"))
                .andExpect(jsonPath("$.body").exists());
    }

    private ResultActions error(MockHttpServletRequestBuilder request, boolean authorized, int status) throws Exception {
        if (authorized) request.header("x-api-key", "123456");
        return mvc.perform(request).andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.body").value(nullValue()))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    private static Stream<Arguments> businessRequests() {
        return Stream.of(Arguments.of("GET", "/polizas"), Arguments.of("GET", "/polizas/1/riesgos"),
                Arguments.of("POST", "/polizas/1/renovar"), Arguments.of("POST", "/polizas/1/cancelar"),
                Arguments.of("POST", "/polizas/2/riesgos"), Arguments.of("POST", "/riesgos/201/cancelar"));
    }

    private MockHttpServletRequestBuilder request(String method, String path) {
        return method.equals("GET") ? get(path) : post(path).contentType(MediaType.APPLICATION_JSON).content("{\"id\":501}");
    }

    private List<PolizaDto> snapshot() {
        return repository.listar(null, null).stream().map(PolizaDto::from).toList();
    }

    private Poliza poliza(Long id, TipoPoliza tipo, String canon, Long... riesgos) {
        return new Poliza(id, tipo, LocalDate.of(2026, 1, 1), 12, new BigDecimal(canon),
                java.util.Arrays.stream(riesgos).map(Riesgo::new).toList());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CoreConfig {
        @Bean @Primary
        TestCore testCore() { return new TestCore(); }
    }

    static class TestCore implements CoreNotificationPort {
        final List<Long> events = new CopyOnWriteArrayList<>();
        boolean fail;
        @Override
        public void notificarActualizacion(Long id) {
            events.add(id);
            if (fail) throw new CoreNotificationException("Detalle interno de conexion simulado",
                    new IllegalStateException("private-core-body sensitive-test-token"));
        }
    }
}
