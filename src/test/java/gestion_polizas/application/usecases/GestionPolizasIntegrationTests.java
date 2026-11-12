package gestion_polizas.application.usecases;

import gestion_polizas.domain.enums.EstadoPoliza;
import gestion_polizas.domain.enums.EstadoRiesgo;
import gestion_polizas.domain.enums.TipoPoliza;
import gestion_polizas.domain.exceptions.ConcurrentPolizaModificationException;
import gestion_polizas.domain.exceptions.CoreNotificationException;
import gestion_polizas.domain.exceptions.ResourceNotFoundException;
import gestion_polizas.domain.model.Poliza;
import gestion_polizas.domain.model.Riesgo;
import gestion_polizas.domain.ports.out.CoreNotificationPort;
import gestion_polizas.domain.ports.out.PolizaRepositoryPort;
import gestion_polizas.infrastructure.adapters.output.persistence.PolizaPersistenceAdapter;
import gestion_polizas.infrastructure.adapters.output.persistence.PolizaPersistenceMapper;
import gestion_polizas.infrastructure.config.ApplicationConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.renovacion.ipc-porcentaje=5"
})
@Import({PolizaPersistenceAdapter.class, PolizaPersistenceMapper.class,
        ApplicationConfig.class, GestionPolizasIntegrationTests.CoreTestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GestionPolizasIntegrationTests {
    @Autowired GestionPolizasService service;
    @Autowired PolizaRepositoryPort repository;
    @Autowired RecordingCore core;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void seed() {
        jdbc.update("delete from riesgos");
        jdbc.update("delete from polizas");
        repository.guardar(new Poliza(1L, TipoPoliza.COLECTIVA, LocalDate.of(2026, 1, 1), 12,
                new BigDecimal("1000"), List.of(new Riesgo(10L), new Riesgo(20L))));
        repository.guardar(new Poliza(2L, TipoPoliza.INDIVIDUAL, LocalDate.of(2026, 1, 1), 12,
                new BigDecimal("2000"), List.of(new Riesgo(30L))));
        core.events.clear();
        core.fail = false;
    }

    @Test
    void consultasConFiltrosYRiesgosNoNotifican() {
        assertThat(service.listar(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA))
                .extracting(p -> p.id()).containsExactly(1L);
        assertThat(service.listar(null, null)).hasSize(2);
        assertThat(service.consultarRiesgos(1L)).extracting(r -> r.id()).containsExactly(10L, 20L);
        assertThat(core.events).isEmpty();
    }

    @Test
    void renovacionesSucesivasConIpcConfiguradoPersistenYNotifican() {
        Long initial = repository.buscarPorId(1L).orElseThrow().getRevision();
        service.renovar(1L);
        var renewed = service.renovar(1L);
        assertThat(renewed.canonMensual()).isEqualTo(new BigDecimal("1102.50"));
        assertThat(renewed.prima()).isEqualTo(new BigDecimal("13230.00"));
        assertThat(renewed.fechaInicioVigencia()).isEqualTo(LocalDate.of(2028, 1, 1));
        assertThat(renewed.revision()).isEqualTo(initial + 2);
        assertThat(repository.buscarPorId(1L).orElseThrow().getRevision()).isEqualTo(renewed.revision());
        assertThat(core.events).containsExactly(1L, 1L);
    }

    @Test
    void cancelarPolizaEsAtomicoEIdempotente() {
        var cancelled = service.cancelar(1L);
        var repeated = service.cancelar(1L);
        assertThat(cancelled).isEqualTo(repeated);
        assertThat(cancelled.estado()).isEqualTo(EstadoPoliza.CANCELADA);
        assertThat(cancelled.riesgos()).extracting(r -> r.estado()).containsOnly(EstadoRiesgo.CANCELADO);
        assertThat(repository.buscarPorId(1L).orElseThrow().getEstado()).isEqualTo(EstadoPoliza.CANCELADA);
        assertThat(jdbc.queryForObject("select count(*) from riesgos where poliza_id = 1", Long.class)).isEqualTo(2);
        assertThat(core.events).containsExactly(1L);
    }

    @Test
    void altaDeRiesgoPersisteYNotifica() {
        var updated = service.agregarRiesgo(1L, 40L);
        assertThat(updated.riesgos()).extracting(r -> r.id()).containsExactly(10L, 20L, 40L);
        assertThat(repository.buscarPorRiesgoId(40L).orElseThrow().getId()).isEqualTo(1L);
        assertThat(core.events).containsExactly(1L);
    }

    @Test
    void cancelarRiesgoEsIdempotenteYNoModificaLosOtros() {
        var cancelled = service.cancelarRiesgo(10L);
        var repeated = service.cancelarRiesgo(10L);
        assertThat(cancelled).isEqualTo(repeated);
        assertThat(cancelled.riesgos()).extracting(r -> r.estado())
                .containsExactly(EstadoRiesgo.CANCELADO, EstadoRiesgo.ACTIVO);
        service.renovar(1L);
        assertThat(repository.buscarPorId(1L).orElseThrow().getRiesgos().getFirst().getEstado())
                .isEqualTo(EstadoRiesgo.CANCELADO);
        assertThat(core.events).containsExactly(1L, 1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"renovar", "cancelar", "agregar", "cancelarRiesgo"})
    void falloCoreRevierteCambiosYVersionEnTransaccionReal(String operation) {
        Poliza before = repository.buscarPorId(1L).orElseThrow();
        core.fail = true;
        assertThatThrownBy(() -> ejecutar(operation)).isInstanceOf(CoreNotificationException.class);

        // There is no surrounding test transaction: this reads the committed database after rollback.
        Poliza after = repository.buscarPorId(1L).orElseThrow();
        assertThat(after).usingRecursiveComparison().isEqualTo(before);
        assertThat(repository.buscarPorRiesgoId(40L)).isEmpty();
        assertThat(core.events).containsExactly(1L);
    }

    @Test
    void recursosInexistentesNoNotifican() {
        assertThatThrownBy(() -> service.consultarRiesgos(999L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.renovar(999L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.cancelar(999L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.agregarRiesgo(999L, 40L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.cancelarRiesgo(999L)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(core.events).isEmpty();
    }

    @Test
    void snapshotObsoletoNoSobrescribeCambiosDeOtroCasoDeUso() {
        Poliza old = repository.buscarPorId(1L).orElseThrow();
        service.renovar(1L);
        Poliza current = repository.buscarPorId(1L).orElseThrow();
        old.cancelar();
        assertThatThrownBy(() -> repository.guardar(old))
                .isInstanceOf(ConcurrentPolizaModificationException.class);
        assertThat(repository.buscarPorId(1L).orElseThrow()).usingRecursiveComparison().isEqualTo(current);
        assertThat(core.events).containsExactly(1L);
    }

    @Test
    void cambioExclusivoDeRiesgoIncrementaVersionDelAgregado() {
        Poliza old = repository.buscarPorId(1L).orElseThrow();
        service.cancelarRiesgo(10L);
        Poliza current = repository.buscarPorId(1L).orElseThrow();
        assertThat(current.getRevision()).isEqualTo(old.getRevision() + 1);
        old.renovar(BigDecimal.ZERO);
        assertThatThrownBy(() -> repository.guardar(old))
                .isInstanceOf(ConcurrentPolizaModificationException.class);
        assertThat(repository.buscarPorId(1L).orElseThrow()).usingRecursiveComparison().isEqualTo(current);
    }

    @Test
    void agregadoSinRevisionNoPuedeSobrescribirUnoExistente() {
        Poliza unversioned = new Poliza(1L, TipoPoliza.COLECTIVA, LocalDate.of(2026, 1, 1), 12,
                BigDecimal.TEN, List.of(new Riesgo(10L), new Riesgo(20L)));
        assertThatThrownBy(() -> repository.guardar(unversioned))
                .isInstanceOf(ConcurrentPolizaModificationException.class);
    }

    @Test
    void dosTransaccionesSimultaneasNoPierdenActualizacionesDeRiesgos() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        Long initialRevision = repository.buscarPorId(1L).orElseThrow().getRevision();
        try {
            var first = executor.submit(() -> actualizarEnParalelo(10L, barrier));
            var second = executor.submit(() -> actualizarEnParalelo(20L, barrier));
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
        Poliza current = repository.buscarPorId(1L).orElseThrow();
        assertThat(current.getRevision()).isEqualTo(initialRevision + 1);
        assertThat(current.getRiesgos().stream().filter(r -> r.getEstado() == EstadoRiesgo.CANCELADO).count())
                .isEqualTo(1);
        assertThat(core.events).isEmpty();
    }

    private boolean actualizarEnParalelo(Long riesgoId, CyclicBarrier barrier) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                Poliza poliza = repository.buscarPorId(1L).orElseThrow();
                poliza.cancelarRiesgo(riesgoId);
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    throw new IllegalStateException("No se sincronizaron las transacciones de prueba", ex);
                }
                repository.guardar(poliza);
            });
            return true;
        } catch (ConcurrentPolizaModificationException ex) {
            return false;
        }
    }

    private void ejecutar(String operation) {
        switch (operation) {
            case "renovar" -> service.renovar(1L);
            case "cancelar" -> service.cancelar(1L);
            case "agregar" -> service.agregarRiesgo(1L, 40L);
            case "cancelarRiesgo" -> service.cancelarRiesgo(10L);
            default -> throw new AssertionError(operation);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CoreTestConfig {
        @Bean
        @Primary
        RecordingCore recordingCore() { return new RecordingCore(); }
    }

    static class RecordingCore implements CoreNotificationPort {
        final List<Long> events = new ArrayList<>();
        boolean fail;

        @Override
        public void notificarActualizacion(Long polizaId) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            events.add(polizaId);
            if (fail) throw new CoreNotificationException("Fallo CORE simulado");
        }
    }
}
