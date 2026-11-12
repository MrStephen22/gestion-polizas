package gestion_polizas.infrastructure.adapters.output.persistence;

import gestion_polizas.domain.enums.EstadoPoliza;
import gestion_polizas.domain.enums.TipoPoliza;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "polizas")
public class PolizaJpaEntity {
    @Id
    private Long id;

    // A null version lets Spring Data identify a new entity with an assigned ID.
    @Version
    private Long version;

    // Child-only updates must dirty the aggregate root so @Version is checked at flush.
    @Column(name = "revision_contenido", nullable = false)
    private long revisionContenido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoPoliza tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPoliza estado;

    @Column(name = "canon_mensual", nullable = false, precision = 38, scale = 2)
    private BigDecimal canonMensual;

    @Column(name = "inicio_vigencia", nullable = false)
    private LocalDate fechaInicioVigencia;

    @Column(name = "fin_vigencia", nullable = false)
    private LocalDate fechaFinVigencia;

    @Column(name = "meses_vigencia_inicial", nullable = false)
    private int mesesVigenciaInicial;

    // No REMOVE cascade or orphan removal: cancelled risks remain in the database.
    @OneToMany(mappedBy = "poliza", cascade = CascadeType.PERSIST)
    @OrderColumn(name = "posicion")
    private List<RiesgoJpaEntity> riesgos = new ArrayList<>();

    protected PolizaJpaEntity() {}

    PolizaJpaEntity(Long id, Long version) {
        this.id = id;
        this.version = version;
    }

    void actualizar(TipoPoliza tipo, EstadoPoliza estado, BigDecimal canonMensual,
            LocalDate inicio, LocalDate fin, int meses) {
        this.tipo = tipo;
        this.estado = estado;
        this.canonMensual = canonMensual;
        this.fechaInicioVigencia = inicio;
        this.fechaFinVigencia = fin;
        this.mesesVigenciaInicial = meses;
    }

    void agregarRiesgo(RiesgoJpaEntity riesgo) {
        riesgos.add(riesgo);
    }

    void marcarActualizacion() {
        revisionContenido++;
    }

    public Long getVersion() { return version; }

    public Long getId() { return id; }
    public TipoPoliza getTipo() { return tipo; }
    public EstadoPoliza getEstado() { return estado; }
    public BigDecimal getCanonMensual() { return canonMensual; }
    public LocalDate getFechaInicioVigencia() { return fechaInicioVigencia; }
    public LocalDate getFechaFinVigencia() { return fechaFinVigencia; }
    public int getMesesVigenciaInicial() { return mesesVigenciaInicial; }
    public List<RiesgoJpaEntity> getRiesgos() { return List.copyOf(riesgos); }

    @Transient
    public BigDecimal getPrima() {
        return canonMensual.multiply(BigDecimal.valueOf(mesesVigenciaInicial));
    }
}
