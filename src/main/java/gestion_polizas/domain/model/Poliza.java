package gestion_polizas.domain.model;

import gestion_polizas.domain.enums.EstadoPoliza;
import gestion_polizas.domain.enums.EstadoRiesgo;
import gestion_polizas.domain.enums.TipoPoliza;
import gestion_polizas.domain.exceptions.BusinessConflictException;
import gestion_polizas.domain.exceptions.InvalidDomainDataException;
import gestion_polizas.domain.exceptions.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Aggregate root. Validity uses the interval [start, end). */
public final class Poliza {
    private final Long id;
    private final TipoPoliza tipo;
    private final int mesesVigenciaInicial;
    private final List<Riesgo> riesgos;
    private EstadoPoliza estado;
    private LocalDate fechaInicioVigencia;
    private LocalDate fechaFinVigencia;
    private BigDecimal canonMensual;

    public Poliza(Long id, TipoPoliza tipo, LocalDate fechaInicioVigencia,
            int mesesVigenciaInicial, BigDecimal canonMensual, List<Riesgo> riesgos) {
        if (id == null || tipo == null || fechaInicioVigencia == null) {
            throw new InvalidDomainDataException("Identificador, tipo e inicio de vigencia son obligatorios");
        }
        if (mesesVigenciaInicial <= 0) {
            throw new InvalidDomainDataException("La vigencia debe tener un numero positivo de meses completos");
        }
        if (canonMensual == null || canonMensual.signum() <= 0) {
            throw new InvalidDomainDataException("El canon mensual debe ser positivo");
        }
        BigDecimal canonRedondeado = redondear(canonMensual);
        if (canonRedondeado.signum() == 0) {
            throw new InvalidDomainDataException("El canon mensual debe ser positivo al redondear a dos decimales");
        }
        validarRiesgos(tipo, riesgos);
        this.id = id;
        this.tipo = tipo;
        this.mesesVigenciaInicial = mesesVigenciaInicial;
        this.fechaInicioVigencia = fechaInicioVigencia;
        this.fechaFinVigencia = finDePeriodo(fechaInicioVigencia, mesesVigenciaInicial);
        this.canonMensual = canonRedondeado;
        this.riesgos = new ArrayList<>(riesgos);
        this.estado = EstadoPoliza.ACTIVA;
    }

    public void agregarRiesgo(Riesgo riesgo) {
        if (tipo != TipoPoliza.COLECTIVA || estado == EstadoPoliza.CANCELADA) {
            throw new BusinessConflictException("Solo se pueden agregar riesgos a polizas colectivas no canceladas");
        }
        if (riesgo == null) {
            throw new InvalidDomainDataException("El riesgo es obligatorio");
        }
        if (riesgos.stream().anyMatch(actual -> actual.getId().equals(riesgo.getId()))) {
            throw new BusinessConflictException("El riesgo ya pertenece a la poliza");
        }
        riesgos.add(riesgo);
    }

    /** The IPC is a percentage: 5 means 5%. Each invocation opens another period. */
    public void renovar(BigDecimal ipcPorcentaje) {
        if (estado == EstadoPoliza.CANCELADA) {
            throw new BusinessConflictException("No se puede renovar una poliza cancelada");
        }
        if (ipcPorcentaje == null || ipcPorcentaje.signum() < 0) {
            throw new InvalidDomainDataException("El IPC debe ser mayor o igual a cero");
        }
        BigDecimal nuevoCanon = redondear(canonMensual.multiply(
                BigDecimal.ONE.add(ipcPorcentaje.movePointLeft(2))));
        LocalDate nuevoInicio = fechaFinVigencia;
        LocalDate nuevoFin = finDePeriodo(nuevoInicio, mesesVigenciaInicial);
        // Calculate everything first so an invalid period cannot partially mutate the aggregate.
        canonMensual = nuevoCanon;
        fechaInicioVigencia = nuevoInicio;
        fechaFinVigencia = nuevoFin;
        estado = EstadoPoliza.RENOVADA;
    }

    /** Returns false when the aggregate was already cancelled. */
    public boolean cancelar() {
        if (estado == EstadoPoliza.CANCELADA) {
            return false;
        }
        riesgos.replaceAll(Riesgo::cancelar);
        estado = EstadoPoliza.CANCELADA;
        return true;
    }

    /** Cancellation retains the risk identity and position in the aggregate. */
    public boolean cancelarRiesgo(Long riesgoId) {
        if (riesgoId == null) {
            throw new InvalidDomainDataException("El identificador del riesgo es obligatorio");
        }
        for (int index = 0; index < riesgos.size(); index++) {
            Riesgo riesgo = riesgos.get(index);
            if (riesgo.getId().equals(riesgoId)) {
                if (riesgo.getEstado() == EstadoRiesgo.CANCELADO) {
                    return false;
                }
                riesgos.set(index, riesgo.cancelar());
                return true;
            }
        }
        throw new ResourceNotFoundException("El riesgo no pertenece a la poliza");
    }

    private static void validarRiesgos(TipoPoliza tipo, List<Riesgo> riesgos) {
        if (riesgos == null || riesgos.isEmpty()) {
            throw new InvalidDomainDataException("La poliza debe tener al menos un riesgo");
        }
        if (tipo == TipoPoliza.INDIVIDUAL && riesgos.size() != 1) {
            throw new InvalidDomainDataException("Una poliza individual debe tener exactamente un riesgo");
        }
        var ids = new HashSet<Long>();
        for (Riesgo riesgo : riesgos) {
            if (riesgo == null || !ids.add(riesgo.getId())) {
                throw new InvalidDomainDataException("Los riesgos deben existir y tener identificadores distintos");
            }
        }
    }

    private static BigDecimal redondear(BigDecimal importe) {
        return importe.setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate finDePeriodo(LocalDate inicio, int meses) {
        try {
            return inicio.plusMonths(meses);
        } catch (DateTimeException ex) {
            throw new InvalidDomainDataException("El periodo de vigencia excede el rango de fechas admitido");
        }
    }

    public Long getId() {
        return id;
    }

    public TipoPoliza getTipo() {
        return tipo;
    }

    public EstadoPoliza getEstado() {
        return estado;
    }

    public LocalDate getFechaInicioVigencia() {
        return fechaInicioVigencia;
    }

    public LocalDate getFechaFinVigencia() {
        return fechaFinVigencia;
    }

    public int getMesesVigenciaInicial() {
        return mesesVigenciaInicial;
    }

    public BigDecimal getCanonMensual() {
        return canonMensual;
    }

    public BigDecimal getPrima() {
        return canonMensual.multiply(BigDecimal.valueOf(mesesVigenciaInicial));
    }

    /** Immutable snapshot; Riesgo itself is also immutable. */
    public List<Riesgo> getRiesgos() {
        return List.copyOf(riesgos);
    }
}
