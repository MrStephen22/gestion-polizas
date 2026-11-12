package gestion_polizas.domain.model;

import gestion_polizas.domain.enums.EstadoPoliza;
import gestion_polizas.domain.enums.EstadoRiesgo;
import gestion_polizas.domain.enums.TipoPoliza;
import gestion_polizas.domain.exceptions.BusinessConflictException;
import gestion_polizas.domain.exceptions.InvalidDomainDataException;
import gestion_polizas.domain.exceptions.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolizaTests {
    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);

    @Test
    void crearIndividualConUnRiesgo() {
        Poliza poliza = individual();

        assertThat(poliza.getId()).isEqualTo(1L);
        assertThat(poliza.getTipo()).isEqualTo(TipoPoliza.INDIVIDUAL);
        assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.ACTIVA);
        assertThat(poliza.getRiesgos()).extracting(Riesgo::getId).containsExactly(10L);
        assertThat(poliza.getRiesgos()).extracting(Riesgo::getEstado).containsExactly(EstadoRiesgo.ACTIVO);
        assertThat(poliza.getFechaInicioVigencia()).isEqualTo(INICIO);
        assertThat(poliza.getFechaFinVigencia()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(poliza.getMesesVigenciaInicial()).isEqualTo(12);
        assertThat(poliza.getPrima()).isEqualTo(new BigDecimal("12000000.00"));
    }

    @Test
    void crearColectivaConVariosRiesgos() {
        assertThat(colectiva().getRiesgos()).extracting(Riesgo::getId).containsExactly(10L, 20L);
    }

    @Test
    void crearColectivaConUnRiesgo() {
        assertThat(crear(TipoPoliza.COLECTIVA, List.of(new Riesgo(10L))).getRiesgos()).hasSize(1);
    }

    @Test
    void rechazarIndividualConMultiplesRiesgos() {
        assertThatThrownBy(() -> crear(TipoPoliza.INDIVIDUAL, List.of(new Riesgo(10L), new Riesgo(20L))))
                .isInstanceOf(InvalidDomainDataException.class);
    }

    @ParameterizedTest
    @EnumSource(TipoPoliza.class)
    void rechazarPolizasSinRiesgos(TipoPoliza tipo) {
        assertThatThrownBy(() -> crear(tipo, List.of())).isInstanceOf(InvalidDomainDataException.class);
        assertThatThrownBy(() -> crear(tipo, null)).isInstanceOf(InvalidDomainDataException.class);
    }

    @Test
    void rechazarRiesgosNulosODuplicados() {
        assertThatThrownBy(() -> crear(TipoPoliza.COLECTIVA, Arrays.asList(new Riesgo(10L), null)))
                .isInstanceOf(InvalidDomainDataException.class);
        assertThatThrownBy(() -> crear(TipoPoliza.COLECTIVA, List.of(new Riesgo(10L), new Riesgo(10L))))
                .isInstanceOf(InvalidDomainDataException.class);
    }

    @Test
    void agregarRiesgoAColectivaActivaYRenovada() {
        Poliza poliza = colectiva();
        poliza.agregarRiesgo(new Riesgo(30L));
        poliza.renovar(BigDecimal.ZERO);
        poliza.agregarRiesgo(new Riesgo(40L));

        assertThat(poliza.getRiesgos()).extracting(Riesgo::getId).containsExactly(10L, 20L, 30L, 40L);
    }

    @Test
    void rechazarAgregarRiesgoAIndividual() {
        Poliza poliza = individual();

        assertThatThrownBy(() -> poliza.agregarRiesgo(new Riesgo(20L)))
                .isInstanceOf(BusinessConflictException.class);
        assertThat(poliza.getRiesgos()).hasSize(1);
    }

    @Test
    void rechazarAgregarRiesgoAColectivaCancelada() {
        Poliza poliza = colectiva();
        poliza.cancelar();

        assertThatThrownBy(() -> poliza.agregarRiesgo(new Riesgo(30L)))
                .isInstanceOf(BusinessConflictException.class);
        assertThat(poliza.getRiesgos()).hasSize(2);
    }

    @Test
    void rechazarAgregarRiesgoNuloODuplicadoSinAlterarColeccion() {
        Poliza poliza = colectiva();

        assertThatThrownBy(() -> poliza.agregarRiesgo(null)).isInstanceOf(InvalidDomainDataException.class);
        assertThatThrownBy(() -> poliza.agregarRiesgo(new Riesgo(10L)))
                .isInstanceOf(BusinessConflictException.class);
        assertThat(poliza.getRiesgos()).hasSize(2);
    }

    @ParameterizedTest
    @EnumSource(TipoPoliza.class)
    void renovarConIpcPorcentualYConservarDuracion(TipoPoliza tipo) {
        Poliza poliza = crear(tipo, List.of(new Riesgo(10L)));

        poliza.renovar(new BigDecimal("5"));

        assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.RENOVADA);
        assertThat(poliza.getCanonMensual()).isEqualTo(new BigDecimal("1050000.00"));
        assertThat(poliza.getPrima()).isEqualTo(new BigDecimal("12600000.00"));
        assertThat(poliza.getFechaInicioVigencia()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(poliza.getFechaFinVigencia()).isEqualTo(LocalDate.of(2028, 1, 1));
        assertThat(poliza.getMesesVigenciaInicial()).isEqualTo(12);
    }

    @Test
    void ipcCeroAbreNuevoPeriodoSinModificarImportes() {
        Poliza poliza = individual();

        poliza.renovar(BigDecimal.ZERO);

        assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.RENOVADA);
        assertThat(poliza.getCanonMensual()).isEqualTo(new BigDecimal("1000000.00"));
        assertThat(poliza.getPrima()).isEqualTo(new BigDecimal("12000000.00"));
        assertThat(poliza.getFechaInicioVigencia()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void renovarSucesivamenteAplicaIpcSobreCanonActualYAvanzaPeriodos() {
        Poliza poliza = individual();
        poliza.renovar(new BigDecimal("5"));
        poliza.renovar(new BigDecimal("10"));

        assertThat(poliza.getCanonMensual()).isEqualTo(new BigDecimal("1155000.00"));
        assertThat(poliza.getPrima()).isEqualTo(new BigDecimal("13860000.00"));
        assertThat(poliza.getFechaInicioVigencia()).isEqualTo(LocalDate.of(2028, 1, 1));
        assertThat(poliza.getFechaFinVigencia()).isEqualTo(LocalDate.of(2029, 1, 1));
        assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.RENOVADA);
    }

    @Test
    void redondearCanonHalfUpYDerivarPrimaEnCadaPeriodo() {
        Poliza poliza = new Poliza(1L, TipoPoliza.INDIVIDUAL, INICIO, 3,
                new BigDecimal("100.005"), List.of(new Riesgo(10L)));
        assertThat(poliza.getCanonMensual()).isEqualTo(new BigDecimal("100.01"));
        assertThat(poliza.getPrima()).isEqualTo(new BigDecimal("300.03"));

        poliza.renovar(new BigDecimal("5"));
        assertThat(poliza.getCanonMensual()).isEqualTo(new BigDecimal("105.01"));
        assertThat(poliza.getPrima()).isEqualTo(new BigDecimal("315.03"));

        poliza.renovar(new BigDecimal("5"));
        assertThat(poliza.getCanonMensual()).isEqualTo(new BigDecimal("110.26"));
        assertThat(poliza.getPrima()).isEqualTo(new BigDecimal("330.78"));
        assertThat(poliza.getFechaInicioVigencia()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(poliza.getFechaFinVigencia()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void redondearMitadDeCentavoEnRenovacion() {
        Poliza poliza = new Poliza(1L, TipoPoliza.INDIVIDUAL, INICIO, 1,
                new BigDecimal("0.10"), List.of(new Riesgo(10L)));
        poliza.renovar(new BigDecimal("5"));
        assertThat(poliza.getCanonMensual()).isEqualTo(new BigDecimal("0.11"));
        assertThat(poliza.getPrima()).isEqualTo(new BigDecimal("0.11"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"-1", "-0.0001"})
    void rechazarIpcInvalidoSinCambiarPoliza(String ipc) {
        Poliza poliza = individual();
        BigDecimal porcentaje = ipc == null ? null : new BigDecimal(ipc);

        assertThatThrownBy(() -> poliza.renovar(porcentaje)).isInstanceOf(InvalidDomainDataException.class);
        assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.ACTIVA);
        assertThat(poliza.getCanonMensual()).isEqualTo(new BigDecimal("1000000.00"));
        assertThat(poliza.getFechaInicioVigencia()).isEqualTo(INICIO);
        assertThat(poliza.getFechaFinVigencia()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void rechazarRenovacionDeCanceladaSinAlterarImportesOVigencia() {
        Poliza poliza = individual();
        poliza.cancelar();

        assertThatThrownBy(() -> poliza.renovar(new BigDecimal("5")))
                .isInstanceOf(BusinessConflictException.class);
        assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.CANCELADA);
        assertThat(poliza.getCanonMensual()).isEqualTo(new BigDecimal("1000000.00"));
        assertThat(poliza.getFechaInicioVigencia()).isEqualTo(INICIO);
        assertThat(poliza.getRiesgos().getFirst().getEstado()).isEqualTo(EstadoRiesgo.CANCELADO);
    }

    @ParameterizedTest
    @EnumSource(TipoPoliza.class)
    void cancelarPolizaCancelaTodosSusRiesgosYEsIdempotente(TipoPoliza tipo) {
        Poliza poliza = tipo == TipoPoliza.INDIVIDUAL ? individual() : colectiva();
        List<Long> ids = poliza.getRiesgos().stream().map(Riesgo::getId).toList();

        assertThat(poliza.cancelar()).isTrue();
        assertThat(poliza.cancelar()).isFalse();
        assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.CANCELADA);
        assertThat(poliza.getRiesgos()).extracting(Riesgo::getEstado).containsOnly(EstadoRiesgo.CANCELADO);
        assertThat(poliza.getRiesgos()).extracting(Riesgo::getId).containsExactlyElementsOf(ids);
        assertThat(poliza.getCanonMensual()).isEqualTo(new BigDecimal("1000000.00"));
        assertThat(poliza.getPrima()).isEqualTo(new BigDecimal("12000000.00"));
        assertThat(poliza.getFechaInicioVigencia()).isEqualTo(INICIO);
    }

    @Test
    void cancelarPolizaRenovadaConRiesgoPreviamenteCancelado() {
        Poliza poliza = colectiva();
        poliza.cancelarRiesgo(10L);
        poliza.renovar(new BigDecimal("5"));

        assertThat(poliza.cancelar()).isTrue();
        assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.CANCELADA);
        assertThat(poliza.getRiesgos()).extracting(Riesgo::getEstado).containsOnly(EstadoRiesgo.CANCELADO);
        assertThat(poliza.getCanonMensual()).isEqualTo(new BigDecimal("1050000.00"));
    }

    @ParameterizedTest
    @EnumSource(TipoPoliza.class)
    void cancelarRiesgoConservaRegistroYEsIdempotente(TipoPoliza tipo) {
        Poliza poliza = tipo == TipoPoliza.INDIVIDUAL ? individual() : colectiva();
        int cantidad = poliza.getRiesgos().size();

        assertThat(poliza.cancelarRiesgo(10L)).isTrue();
        assertThat(poliza.cancelarRiesgo(10L)).isFalse();
        assertThat(poliza.getRiesgos()).hasSize(cantidad);
        assertThat(poliza.getRiesgos().getFirst().getId()).isEqualTo(10L);
        assertThat(poliza.getRiesgos().getFirst().getEstado()).isEqualTo(EstadoRiesgo.CANCELADO);
        assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.ACTIVA);
    }

    @Test
    void cancelarUnRiesgoNoAfectaLosDemasNiLaPrima() {
        Poliza poliza = colectiva();
        poliza.cancelarRiesgo(10L);
        assertThat(poliza.getRiesgos().get(1).getEstado()).isEqualTo(EstadoRiesgo.ACTIVO);
        assertThat(poliza.getPrima()).isEqualTo(new BigDecimal("12000000.00"));
    }

    @Test
    void cancelarUltimoRiesgoActivoNoCancelaAutomaticamenteLaColectiva() {
        Poliza poliza = colectiva();
        poliza.cancelarRiesgo(10L);
        poliza.cancelarRiesgo(20L);
        assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.ACTIVA);
        assertThat(poliza.getRiesgos()).hasSize(2);
    }

    @Test
    void renovarNoReactivaRiesgosCancelados() {
        Poliza poliza = colectiva();
        poliza.cancelarRiesgo(10L);
        poliza.renovar(new BigDecimal("5"));
        poliza.renovar(BigDecimal.ZERO);

        assertThat(poliza.getRiesgos()).extracting(Riesgo::getId).containsExactly(10L, 20L);
        assertThat(poliza.getRiesgos()).extracting(Riesgo::getEstado)
                .containsExactly(EstadoRiesgo.CANCELADO, EstadoRiesgo.ACTIVO);
    }

    @Test
    void cancelarRiesgoDePolizaCanceladaNoProduceOtroCambio() {
        Poliza poliza = colectiva();
        poliza.cancelar();
        assertThat(poliza.cancelarRiesgo(10L)).isFalse();
    }

    @Test
    void rechazarCancelacionDeRiesgoInexistente() {
        Poliza poliza = individual();
        assertThatThrownBy(() -> poliza.cancelarRiesgo(999L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> poliza.cancelarRiesgo(null)).isInstanceOf(InvalidDomainDataException.class);
        assertThat(poliza.getRiesgos().getFirst().getEstado()).isEqualTo(EstadoRiesgo.ACTIVO);
    }

    @Test
    void protegerColeccionYReferenciasExternas() {
        Riesgo original = new Riesgo(10L);
        var entrada = new ArrayList<>(List.of(original));
        Poliza poliza = crear(TipoPoliza.COLECTIVA, entrada);
        List<Riesgo> snapshot = poliza.getRiesgos();
        entrada.clear();

        assertThatThrownBy(() -> snapshot.add(new Riesgo(20L)))
                .isInstanceOf(UnsupportedOperationException.class);
        poliza.cancelarRiesgo(10L);
        assertThat(original.getEstado()).isEqualTo(EstadoRiesgo.ACTIVO);
        assertThat(snapshot.getFirst().getEstado()).isEqualTo(EstadoRiesgo.ACTIVO);
        assertThat(poliza.getRiesgos().getFirst().getEstado()).isEqualTo(EstadoRiesgo.CANCELADO);
        assertThat(poliza.getRiesgos()).hasSize(1);
    }

    @Test
    void vigenciasEnAnioBisiestoConservanDuracionEnMeses() {
        Poliza poliza = new Poliza(1L, TipoPoliza.INDIVIDUAL, LocalDate.of(2024, 2, 29), 12,
                BigDecimal.TEN, List.of(new Riesgo(10L)));
        assertThat(poliza.getFechaFinVigencia()).isEqualTo(LocalDate.of(2025, 2, 28));
        poliza.renovar(BigDecimal.ZERO);
        assertThat(poliza.getFechaInicioVigencia()).isEqualTo(LocalDate.of(2025, 2, 28));
        assertThat(poliza.getFechaFinVigencia()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void finDeMesUsaAritmeticaDeLocalDateSinHuecosEntrePeriodos() {
        Poliza poliza = new Poliza(1L, TipoPoliza.INDIVIDUAL, LocalDate.of(2026, 1, 31), 1,
                BigDecimal.TEN, List.of(new Riesgo(10L)));
        assertThat(poliza.getFechaFinVigencia()).isEqualTo(LocalDate.of(2026, 2, 28));
        poliza.renovar(BigDecimal.ZERO);
        assertThat(poliza.getFechaInicioVigencia()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(poliza.getFechaFinVigencia()).isEqualTo(LocalDate.of(2026, 3, 28));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rechazarDuracionNoPositiva(int meses) {
        assertThatThrownBy(() -> new Poliza(1L, TipoPoliza.INDIVIDUAL, INICIO, meses,
                BigDecimal.TEN, List.of(new Riesgo(10L)))).isInstanceOf(InvalidDomainDataException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "0.004"})
    void rechazarCanonInvalido(String canon) {
        assertThatThrownBy(() -> new Poliza(1L, TipoPoliza.INDIVIDUAL, INICIO, 12,
                canon == null ? null : new BigDecimal(canon), List.of(new Riesgo(10L))))
                .isInstanceOf(InvalidDomainDataException.class);
    }

    @Test
    void rechazarDatosObligatoriosAusentes() {
        assertThatThrownBy(() -> new Poliza(null, TipoPoliza.INDIVIDUAL, INICIO, 12,
                BigDecimal.TEN, List.of(new Riesgo(10L)))).isInstanceOf(InvalidDomainDataException.class);
        assertThatThrownBy(() -> new Poliza(1L, null, INICIO, 12,
                BigDecimal.TEN, List.of(new Riesgo(10L)))).isInstanceOf(InvalidDomainDataException.class);
        assertThatThrownBy(() -> new Poliza(1L, TipoPoliza.INDIVIDUAL, null, 12,
                BigDecimal.TEN, List.of(new Riesgo(10L)))).isInstanceOf(InvalidDomainDataException.class);
    }

    @Test
    void rechazarRenovacionFueraDeRangoSinMutacionParcial() {
        LocalDate inicio = LocalDate.of(999999999, 10, 1);
        Poliza poliza = new Poliza(1L, TipoPoliza.INDIVIDUAL, inicio, 2,
                BigDecimal.TEN, List.of(new Riesgo(10L)));

        assertThatThrownBy(() -> poliza.renovar(new BigDecimal("5")))
                .isInstanceOf(InvalidDomainDataException.class);
        assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.ACTIVA);
        assertThat(poliza.getCanonMensual()).isEqualTo(new BigDecimal("10.00"));
        assertThat(poliza.getFechaInicioVigencia()).isEqualTo(inicio);
        assertThat(poliza.getFechaFinVigencia()).isEqualTo(LocalDate.of(999999999, 12, 1));
    }

    private Poliza individual() {
        return crear(TipoPoliza.INDIVIDUAL, List.of(new Riesgo(10L)));
    }

    private Poliza colectiva() {
        return crear(TipoPoliza.COLECTIVA, List.of(new Riesgo(10L), new Riesgo(20L)));
    }

    private Poliza crear(TipoPoliza tipo, List<Riesgo> riesgos) {
        return new Poliza(1L, tipo, INICIO, 12, new BigDecimal("1000000"), riesgos);
    }
}
