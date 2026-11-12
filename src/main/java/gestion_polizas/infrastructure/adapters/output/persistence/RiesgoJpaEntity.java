package gestion_polizas.infrastructure.adapters.output.persistence;

import gestion_polizas.domain.enums.EstadoRiesgo;
import jakarta.persistence.*;

@Entity
@Table(name = "riesgos")
public class RiesgoJpaEntity {
    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoRiesgo estado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poliza_id", nullable = false, updatable = false)
    private PolizaJpaEntity poliza;

    protected RiesgoJpaEntity() {}

    RiesgoJpaEntity(Long id, EstadoRiesgo estado, PolizaJpaEntity poliza) {
        this.id = id;
        this.estado = estado;
        this.poliza = poliza;
    }

    void actualizarEstado(EstadoRiesgo estado) {
        this.estado = estado;
    }

    public Long getId() { return id; }
    public EstadoRiesgo getEstado() { return estado; }
    public PolizaJpaEntity getPoliza() { return poliza; }
}
