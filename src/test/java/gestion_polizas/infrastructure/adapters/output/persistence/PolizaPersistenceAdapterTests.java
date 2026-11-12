package gestion_polizas.infrastructure.adapters.output.persistence;

import gestion_polizas.domain.enums.EstadoPoliza;
import gestion_polizas.domain.enums.EstadoRiesgo;
import gestion_polizas.domain.enums.TipoPoliza;
import gestion_polizas.domain.exceptions.BusinessConflictException;
import gestion_polizas.domain.exceptions.InvalidDomainDataException;
import gestion_polizas.domain.model.Poliza;
import gestion_polizas.domain.model.Riesgo;
import gestion_polizas.domain.ports.out.PolizaRepositoryPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Import({PolizaPersistenceAdapter.class, PolizaPersistenceMapper.class})
class PolizaPersistenceAdapterTests {
    @Autowired PolizaRepositoryPort adapter;
    @Autowired SpringDataPolizaRepository repository;
    @Autowired PolizaPersistenceMapper mapper;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired JdbcTemplate jdbc;

    @Test
    void guardarYRecuperarIndividual() {
        Poliza original = individual(1L, 10L);
        assertEquivalent(original, adapter.guardar(original));
        clear();
        assertEquivalent(original, adapter.buscarPorId(1L).orElseThrow());
    }

    @Test
    void guardarYRecuperarColectivaConOrdenOriginalDeRiesgos() {
        Poliza original = colectiva(1L, 30L, 10L);
        adapter.guardar(original);
        clear();
        Poliza recovered = adapter.buscarPorId(1L).orElseThrow();
        assertEquivalent(original, recovered);
        assertThat(recovered.getRiesgos()).extracting(Riesgo::getId).containsExactly(30L, 10L);
    }

    @Test
    void actualizarAgregaRiesgosSinDuplicarLosExistentes() {
        adapter.guardar(colectiva(1L, 10L, 20L));
        clear();
        Poliza updated = adapter.buscarPorId(1L).orElseThrow();
        updated.agregarRiesgo(new Riesgo(30L));
        updated = adapter.guardar(updated);
        updated = adapter.guardar(updated);
        clear();

        assertEquivalent(updated, adapter.buscarPorId(1L).orElseThrow());
        assertThat(jdbc.queryForObject("select count(*) from riesgos", Long.class)).isEqualTo(3L);
        assertThat(jdbc.queryForList("select poliza_id from riesgos", Long.class)).containsOnly(1L);
    }

    @Test
    void persistirRenovacionesSucesivasSinRepetirlasAlLeer() {
        Poliza original = individual(1L, 10L);
        adapter.guardar(original);
        clear();
        Poliza renewed = adapter.buscarPorId(1L).orElseThrow();
        renewed.renovar(new BigDecimal("5"));
        adapter.guardar(renewed);
        clear();
        renewed = adapter.buscarPorId(1L).orElseThrow();
        renewed.renovar(new BigDecimal("5"));
        adapter.guardar(renewed);
        clear();

        Poliza recovered = adapter.buscarPorId(1L).orElseThrow();
        assertEquivalent(renewed, recovered);
        assertThat(recovered.getEstado()).isEqualTo(EstadoPoliza.RENOVADA);
        assertThat(recovered.getCanonMensual()).isEqualTo(new BigDecimal("1102.51"));
        assertThat(recovered.getPrima()).isEqualTo(new BigDecimal("13230.12"));
        assertThat(recovered.getFechaInicioVigencia()).isEqualTo(LocalDate.of(2028, 1, 1));
    }

    @Test
    void persistirCancelacionDeRiesgoSinEliminarloNiReactivarloAlRenovar() {
        Poliza original = colectiva(1L, 10L, 20L);
        adapter.guardar(original);
        clear();
        original = adapter.buscarPorId(1L).orElseThrow();
        original.cancelarRiesgo(10L);
        adapter.guardar(original);
        clear();
        original = adapter.buscarPorId(1L).orElseThrow();
        original.renovar(new BigDecimal("5"));
        adapter.guardar(original);
        clear();

        Poliza recovered = adapter.buscarPorId(1L).orElseThrow();
        assertEquivalent(original, recovered);
        assertThat(recovered.getRiesgos()).extracting(Riesgo::getEstado)
                .containsExactly(EstadoRiesgo.CANCELADO, EstadoRiesgo.ACTIVO);
        assertThat(jdbc.queryForObject("select count(*) from riesgos", Long.class)).isEqualTo(2L);
        assertThat(recovered.cancelarRiesgo(10L)).isFalse();
    }

    @Test
    void persistirCancelacionDePolizaYTodosSusRiesgos() {
        Poliza original = colectiva(1L, 10L, 20L);
        original = adapter.guardar(original);
        original.cancelar();
        adapter.guardar(original);
        clear();

        Poliza recovered = adapter.buscarPorId(1L).orElseThrow();
        assertEquivalent(original, recovered);
        assertThat(recovered.cancelar()).isFalse();
        assertThat(recovered.getEstado()).isEqualTo(EstadoPoliza.CANCELADA);
        assertThat(recovered.getRiesgos()).extracting(Riesgo::getEstado).containsOnly(EstadoRiesgo.CANCELADO);
        assertThat(jdbc.queryForObject("select count(*) from riesgos where estado = 'CANCELADO'", Long.class))
                .isEqualTo(2L);
    }

    @Test
    void insertarAgregadoYaRenovadoOCancelado() {
        Poliza renewed = individual(1L, 10L);
        renewed.renovar(new BigDecimal("5"));
        Poliza cancelled = colectiva(2L, 20L, 30L);
        cancelled.cancelar();
        adapter.guardar(renewed);
        adapter.guardar(cancelled);
        clear();
        assertEquivalent(renewed, adapter.buscarPorId(1L).orElseThrow());
        assertEquivalent(cancelled, adapter.buscarPorId(2L).orElseThrow());
    }

    @Test
    void buscarPorRiesgoDevuelvePropietariaConTodosLosRiesgosInclusoCancelados() {
        Poliza original = colectiva(1L, 10L, 20L);
        original.cancelarRiesgo(10L);
        adapter.guardar(original);
        adapter.guardar(individual(2L, 30L));
        clear();

        assertEquivalent(original, adapter.buscarPorRiesgoId(10L).orElseThrow());
        assertThat(adapter.buscarPorRiesgoId(999L)).isEmpty();
        assertThat(adapter.buscarPorId(999L)).isEmpty();
    }

    @Test
    void filtrosOpcionalesYOrdenEstable() {
        Poliza renewed = colectiva(3L, 30L, 31L);
        renewed.renovar(new BigDecimal("5"));
        Poliza cancelled = individual(2L, 20L);
        cancelled.cancelar();
        adapter.guardar(renewed);
        adapter.guardar(individual(1L, 10L));
        adapter.guardar(cancelled);
        clear();

        assertThat(adapter.listar(null, null)).extracting(Poliza::getId).containsExactly(1L, 2L, 3L);
        assertThat(adapter.listar(TipoPoliza.INDIVIDUAL, null)).extracting(Poliza::getId).containsExactly(1L, 2L);
        assertThat(adapter.listar(null, EstadoPoliza.CANCELADA)).extracting(Poliza::getId).containsExactly(2L);
        assertThat(adapter.listar(TipoPoliza.COLECTIVA, EstadoPoliza.RENOVADA))
                .extracting(Poliza::getId).containsExactly(3L);
        assertThat(adapter.listar(TipoPoliza.COLECTIVA, EstadoPoliza.CANCELADA)).isEmpty();
    }

    @Test
    void lecturasConRiesgosUsanUnaConsultaSinNMasUno() {
        adapter.guardar(colectiva(1L, 10L, 11L));
        adapter.guardar(colectiva(2L, 20L, 21L));
        clear();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        assertThat(adapter.listar(null, null)).hasSize(2).allSatisfy(p -> assertThat(p.getRiesgos()).hasSize(2));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        clear();
        statistics.clear();
        assertThat(adapter.buscarPorId(1L).orElseThrow().getRiesgos()).hasSize(2);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        clear();
        statistics.clear();
        assertThat(adapter.buscarPorRiesgoId(10L).orElseThrow().getRiesgos()).hasSize(2);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void rechazarRiesgoDeOtraPolizaSinReasignarNiModificarSuEstado() {
        Poliza owner = individual(1L, 10L);
        owner.cancelarRiesgo(10L);
        adapter.guardar(owner);
        clear();

        assertThatThrownBy(() -> adapter.guardar(individual(2L, 10L)))
                .isInstanceOf(BusinessConflictException.class);
        entityManager.clear();
        assertEquivalent(owner, adapter.buscarPorRiesgoId(10L).orElseThrow());
        assertThat(adapter.buscarPorId(2L)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from riesgos", Long.class)).isEqualTo(1L);
    }

    @Test
    void actualizarNoPuedeOmitirRiesgosPersistidos() {
        Poliza original = colectiva(1L, 10L, 20L);
        original = adapter.guardar(original);
        clear();
        Poliza incomplete = Poliza.rehidratar(original.getId(), original.getTipo(), original.getEstado(),
                original.getFechaInicioVigencia(), original.getFechaFinVigencia(), original.getMesesVigenciaInicial(),
                original.getCanonMensual(), List.of(original.getRiesgos().getFirst()), original.getRevision());

        assertThatThrownBy(() -> adapter.guardar(incomplete))
                .isInstanceOf(BusinessConflictException.class);
        entityManager.clear();
        assertEquivalent(original, adapter.buscarPorId(1L).orElseThrow());
    }

    @Test
    void entidadesConservanRelacionBidireccionalYPrimaDerivada() {
        adapter.guardar(colectiva(1L, 10L, 20L));
        clear();
        PolizaJpaEntity entity = repository.buscarConRiesgos(1L).orElseThrow();
        assertThat(entity.getRiesgos()).allSatisfy(r -> assertThat(r.getPoliza()).isSameAs(entity));
        assertThat(entity.getPrima()).isEqualTo(new BigDecimal("12000.12"));
        assertEquivalent(mapper.toDomain(entity), mapper.toDomain(mapper.toEntity(mapper.toDomain(entity))));
    }

    @Test
    void baseDeDatosRechazaIdentificadorDuplicadoDeRiesgo() {
        adapter.guardar(individual(1L, 10L));
        assertThatThrownBy(() -> jdbc.update(
                "insert into riesgos (id, estado, poliza_id, posicion) values (10, 'ACTIVO', 1, 1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void baseDeDatosRechazaRiesgoSinPadreOConPadreInexistente() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into riesgos (id, estado, poliza_id, posicion) values (10, 'ACTIVO', 999, 0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into riesgos (id, estado, poliza_id, posicion) values (20, 'ACTIVO', null, 0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reconstruccionRechazaDatosPersistidosInconsistentes() {
        adapter.guardar(individual(1L, 10L));
        jdbc.update("update polizas set estado = 'CANCELADA' where id = 1");
        entityManager.clear();
        assertThatThrownBy(() -> adapter.buscarPorId(1L)).isInstanceOf(InvalidDomainDataException.class);
    }

    private void clear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Poliza individual(Long id, Long riesgoId) {
        return new Poliza(id, TipoPoliza.INDIVIDUAL, LocalDate.of(2026, 1, 1), 12,
                new BigDecimal("1000.01"), List.of(new Riesgo(riesgoId)));
    }

    private Poliza colectiva(Long id, Long... riesgoIds) {
        return new Poliza(id, TipoPoliza.COLECTIVA, LocalDate.of(2026, 1, 1), 12,
                new BigDecimal("1000.01"), java.util.Arrays.stream(riesgoIds).map(Riesgo::new).toList());
    }

    private void assertEquivalent(Poliza expected, Poliza actual) {
        assertThat(actual).usingRecursiveComparison().ignoringFields("revision").isEqualTo(expected);
        assertThat(actual.getPrima()).isEqualTo(expected.getPrima());
    }
}
