package gestion_polizas.infrastructure.config;

import gestion_polizas.application.dto.PolizaDto;
import gestion_polizas.domain.enums.EstadoPoliza;
import gestion_polizas.domain.enums.EstadoRiesgo;
import gestion_polizas.domain.enums.TipoPoliza;
import gestion_polizas.domain.ports.out.PolizaRepositoryPort;
import gestion_polizas.infrastructure.adapters.output.persistence.PolizaPersistenceAdapter;
import gestion_polizas.infrastructure.adapters.output.persistence.PolizaPersistenceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@Import({PolizaPersistenceAdapter.class, PolizaPersistenceMapper.class})
class DevelopmentDataInitializerTests {
    @Autowired PolizaRepositoryPort repository;
    @Autowired ApplicationContext context;

    @Test
    void datosSoloSeCarganMedianteInvocacionExplicitaEnEstaPrueba() {
        assertThat(context.getBeansOfType(DevelopmentDataInitializer.class)).isEmpty();
        assertThat(repository.listar(null, null)).isEmpty();
        inicializar();
        var polizas = repository.listar(null, null);
        assertThat(polizas).hasSize(4);
        assertThat(polizas.get(0).getTipo()).isEqualTo(TipoPoliza.INDIVIDUAL);
        assertThat(polizas.get(0).getRiesgos()).hasSize(1);
        assertThat(polizas.get(1).getTipo()).isEqualTo(TipoPoliza.COLECTIVA);
        assertThat(polizas.get(1).getRiesgos()).hasSize(3);
        assertThat(polizas.get(2).getEstado()).isEqualTo(EstadoPoliza.RENOVADA);
        assertThat(polizas.get(3).getEstado()).isEqualTo(EstadoPoliza.CANCELADA);
        assertThat(polizas.get(3).getRiesgos()).allSatisfy(r -> assertThat(r.getEstado()).isEqualTo(EstadoRiesgo.CANCELADO));
    }

    @Test
    void reinicializarNoDuplicaNiSobrescribeOperacionesExistentes() {
        inicializar();
        var before = repository.listar(null, null).stream().map(PolizaDto::from).toList();
        inicializar();
        assertThat(repository.listar(null, null).stream().map(PolizaDto::from).toList()).isEqualTo(before);
        var poliza = repository.buscarPorId(2L).orElseThrow();
        poliza.cancelar();
        repository.guardar(poliza);
        inicializar();
        assertThat(repository.buscarPorId(2L).orElseThrow().getEstado()).isEqualTo(EstadoPoliza.CANCELADA);
        assertThat(repository.listar(null, null)).hasSize(4);
    }

    private void inicializar() {
        new DevelopmentDataInitializer(repository).run(new DefaultApplicationArguments());
    }
}
