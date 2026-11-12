package gestion_polizas.domain.model;

import gestion_polizas.domain.enums.EstadoPoliza;
import gestion_polizas.domain.enums.EstadoRiesgo;
import gestion_polizas.domain.enums.TipoPoliza;
import gestion_polizas.domain.exceptions.InvalidDomainDataException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RehidratacionTests {
    private final LocalDate inicio = LocalDate.of(2027, 1, 1);
    private final LocalDate fin = inicio.plusMonths(12);

    @Test
    void restaurarRenovadaNoIncrementaCanonNiAvanzaFechas() {
        Poliza poliza = restaurar(EstadoPoliza.RENOVADA, fin,
                List.of(Riesgo.rehidratar(10L, EstadoRiesgo.CANCELADO)));
        assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.RENOVADA);
        assertThat(poliza.getCanonMensual()).isEqualTo(new BigDecimal("105.00"));
        assertThat(poliza.getPrima()).isEqualTo(new BigDecimal("1260.00"));
        assertThat(poliza.getFechaInicioVigencia()).isEqualTo(inicio);
        assertThat(poliza.getFechaFinVigencia()).isEqualTo(fin);
        assertThat(poliza.getRiesgos().getFirst().getEstado()).isEqualTo(EstadoRiesgo.CANCELADO);
    }

    @Test
    void restaurarCanceladaConservaIdempotencia() {
        Poliza poliza = restaurar(EstadoPoliza.CANCELADA, fin,
                List.of(Riesgo.rehidratar(10L, EstadoRiesgo.CANCELADO)));
        assertThat(poliza.cancelar()).isFalse();
        assertThat(poliza.cancelarRiesgo(10L)).isFalse();
    }

    @Test
    void rechazarCanceladaConRiesgoActivo() {
        assertThatThrownBy(() -> restaurar(EstadoPoliza.CANCELADA, fin, List.of(new Riesgo(10L))))
                .isInstanceOf(InvalidDomainDataException.class);
    }

    @Test
    void rechazarEstadoNuloYPeriodoInconsistente() {
        assertThatThrownBy(() -> restaurar(null, fin, List.of(new Riesgo(10L))))
                .isInstanceOf(InvalidDomainDataException.class);
        assertThatThrownBy(() -> restaurar(EstadoPoliza.ACTIVA, fin.plusDays(1), List.of(new Riesgo(10L))))
                .isInstanceOf(InvalidDomainDataException.class);
        assertThatThrownBy(() -> restaurar(EstadoPoliza.ACTIVA, null, List.of(new Riesgo(10L))))
                .isInstanceOf(InvalidDomainDataException.class);
        assertThatThrownBy(() -> Riesgo.rehidratar(10L, null)).isInstanceOf(InvalidDomainDataException.class);
    }

    @Test
    void reconstruccionMantieneLasValidacionesDeCreacion() {
        assertThatThrownBy(() -> restaurar(EstadoPoliza.ACTIVA, fin, List.of()))
                .isInstanceOf(InvalidDomainDataException.class);
        assertThatThrownBy(() -> restaurar(EstadoPoliza.ACTIVA, fin,
                List.of(new Riesgo(10L), new Riesgo(20L))))
                .isInstanceOf(InvalidDomainDataException.class);
        assertThatThrownBy(() -> Poliza.rehidratar(1L, TipoPoliza.INDIVIDUAL,
                EstadoPoliza.RENOVADA, inicio, fin, 12, BigDecimal.ZERO, List.of(new Riesgo(10L))))
                .isInstanceOf(InvalidDomainDataException.class);
    }

    private Poliza restaurar(EstadoPoliza estado, LocalDate fin, List<Riesgo> riesgos) {
        return Poliza.rehidratar(1L, TipoPoliza.INDIVIDUAL, estado,
                inicio, fin, 12, new BigDecimal("105.00"), riesgos);
    }
}
