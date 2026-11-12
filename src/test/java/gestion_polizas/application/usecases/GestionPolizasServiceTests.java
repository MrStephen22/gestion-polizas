package gestion_polizas.application.usecases;

import gestion_polizas.application.dto.PolizaDto;
import gestion_polizas.domain.enums.EstadoPoliza;
import gestion_polizas.domain.enums.EstadoRiesgo;
import gestion_polizas.domain.enums.TipoPoliza;
import gestion_polizas.domain.exceptions.BusinessConflictException;
import gestion_polizas.domain.exceptions.CoreNotificationException;
import gestion_polizas.domain.exceptions.ResourceNotFoundException;
import gestion_polizas.domain.model.Poliza;
import gestion_polizas.domain.model.Riesgo;
import gestion_polizas.domain.ports.out.CoreNotificationPort;
import gestion_polizas.domain.ports.out.PolizaRepositoryPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GestionPolizasServiceTests {
    @Mock PolizaRepositoryPort repository;
    @Mock CoreNotificationPort core;
    GestionPolizasService service;
    Poliza poliza;

    @BeforeEach
    void setUp() {
        service = new GestionPolizasService(repository, core, new BigDecimal("5"));
        poliza = new Poliza(1L, TipoPoliza.COLECTIVA, LocalDate.of(2026, 1, 1), 12,
                new BigDecimal("1000"), List.of(new Riesgo(10L), new Riesgo(20L)));
    }

    @Test
    void listadoDelegaFiltrosYDevuelveDtosSinNotificar() {
        when(repository.listar(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA)).thenReturn(List.of(poliza));
        assertThat(service.listar(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA))
                .containsExactly(PolizaDto.from(poliza));
        verify(repository).listar(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(core);
    }

    @Test
    void consultaDeRiesgosNoGuardaNiNotifica() {
        when(repository.buscarPorId(1L)).thenReturn(Optional.of(poliza));
        assertThat(service.consultarRiesgos(1L)).extracting(r -> r.id()).containsExactly(10L, 20L);
        verify(repository).buscarPorId(1L);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(core);
    }

    @Test
    void renovacionUtilizaIpcConfiguradoYDtoConPrimaDerivada() {
        cargarParaModificar("renovar");
        PolizaDto result = service.renovar(1L);
        assertThat(result.canonMensual()).isEqualTo(new BigDecimal("1050.00"));
        assertThat(result.prima()).isEqualTo(new BigDecimal("12600.00"));
        assertThat(result.estado()).isEqualTo(EstadoPoliza.RENOVADA);
        assertThat(result.fechaInicioVigencia()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"renovar", "cancelar", "agregar", "cancelarRiesgo"})
    void cambioEfectivoGuardaAntesDeNotificarUnaVez(String operation) {
        cargarParaModificar(operation);
        ejecutar(operation);
        var order = inOrder(repository, core);
        if (operation.equals("cancelarRiesgo")) {
            order.verify(repository).buscarPorRiesgoId(10L);
        } else {
            order.verify(repository).buscarPorId(1L);
        }
        order.verify(repository).guardar(poliza);
        order.verify(core).notificarActualizacion(1L);
        order.verifyNoMoreInteractions();
        if (operation.equals("agregar")) assertThat(poliza.getRiesgos()).hasSize(3);
        if (operation.equals("cancelar")) assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.CANCELADA);
        if (operation.equals("cancelarRiesgo")) {
            assertThat(poliza.getRiesgos().getFirst().getEstado()).isEqualTo(EstadoRiesgo.CANCELADO);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancelar", "cancelarRiesgo"})
    void cancelacionIdempotenteNoGuardaNiNotifica(String operation) {
        poliza.cancelar();
        if (operation.equals("cancelarRiesgo")) {
            when(repository.buscarPorRiesgoId(10L)).thenReturn(Optional.of(poliza));
        } else {
            when(repository.buscarPorId(1L)).thenReturn(Optional.of(poliza));
        }
        ejecutar(operation);
        verify(repository, never()).guardar(any());
        verifyNoInteractions(core);
    }

    @ParameterizedTest
    @ValueSource(strings = {"riesgos", "renovar", "cancelar", "agregar", "cancelarRiesgo"})
    void recursoInexistenteNoGuardaNiNotifica(String operation) {
        assertThatThrownBy(() -> ejecutar(operation)).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).guardar(any());
        verifyNoInteractions(core);
    }

    @Test
    void renovacionInvalidaReutilizaReglaDelDominio() {
        poliza.cancelar();
        when(repository.buscarPorId(1L)).thenReturn(Optional.of(poliza));
        assertThatThrownBy(() -> service.renovar(1L)).isInstanceOf(BusinessConflictException.class);
        verify(repository, never()).guardar(any());
        verifyNoInteractions(core);
    }

    @Test
    void altaEnIndividualReutilizaReglaDelDominio() {
        Poliza individual = new Poliza(1L, TipoPoliza.INDIVIDUAL, LocalDate.of(2026, 1, 1), 12,
                BigDecimal.TEN, List.of(new Riesgo(10L)));
        when(repository.buscarPorId(1L)).thenReturn(Optional.of(individual));
        assertThatThrownBy(() -> service.agregarRiesgo(1L, 30L)).isInstanceOf(BusinessConflictException.class);
        verify(repository, never()).guardar(any());
        verifyNoInteractions(core);
    }

    @Test
    void falloPersistenciaEvitaNotificacion() {
        when(repository.buscarPorId(1L)).thenReturn(Optional.of(poliza));
        when(repository.guardar(poliza)).thenThrow(new BusinessConflictException("conflicto"));
        assertThatThrownBy(() -> service.renovar(1L)).isInstanceOf(BusinessConflictException.class);
        verifyNoInteractions(core);
    }

    @Test
    void falloCoreSePropagaSinReintentar() {
        cargarParaModificar("renovar");
        doThrow(new CoreNotificationException("fallo simulado")).when(core).notificarActualizacion(1L);
        assertThatThrownBy(() -> service.renovar(1L)).isInstanceOf(CoreNotificationException.class);
        verify(core, times(1)).notificarActualizacion(1L);
        verify(repository, times(1)).guardar(poliza);
    }

    private void cargarParaModificar(String operation) {
        if (operation.equals("cancelarRiesgo")) {
            when(repository.buscarPorRiesgoId(10L)).thenReturn(Optional.of(poliza));
        } else {
            when(repository.buscarPorId(1L)).thenReturn(Optional.of(poliza));
        }
        when(repository.guardar(poliza)).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Object ejecutar(String operation) {
        return switch (operation) {
            case "riesgos" -> service.consultarRiesgos(1L);
            case "renovar" -> service.renovar(1L);
            case "cancelar" -> service.cancelar(1L);
            case "agregar" -> service.agregarRiesgo(1L, 30L);
            case "cancelarRiesgo" -> service.cancelarRiesgo(10L);
            default -> throw new AssertionError(operation);
        };
    }
}
