package gestion_polizas.infrastructure.adapters.output.persistence;

import gestion_polizas.domain.exceptions.BusinessConflictException;
import gestion_polizas.domain.model.Poliza;
import gestion_polizas.domain.model.Riesgo;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PolizaPersistenceMapper {
    public PolizaJpaEntity toEntity(Poliza poliza) {
        PolizaJpaEntity entity = new PolizaJpaEntity(poliza.getId(), poliza.getRevision());
        actualizar(poliza, entity);
        return entity;
    }

    /** Updates managed entities without merging detached children or dropping persisted risks. */
    public void actualizar(Poliza poliza, PolizaJpaEntity entity) {
        if (!poliza.getId().equals(entity.getId())) {
            throw new IllegalArgumentException("La identidad de la poliza no coincide");
        }
        Map<Long, Riesgo> incoming = poliza.getRiesgos().stream()
                .collect(Collectors.toMap(Riesgo::getId, Function.identity()));
        Map<Long, RiesgoJpaEntity> existing = entity.getRiesgos().stream()
                .collect(Collectors.toMap(RiesgoJpaEntity::getId, Function.identity()));
        if (!incoming.keySet().containsAll(existing.keySet())) {
            throw new BusinessConflictException("El agregado no puede omitir riesgos persistidos");
        }
        entity.actualizar(poliza.getTipo(), poliza.getEstado(), poliza.getCanonMensual(),
                poliza.getFechaInicioVigencia(), poliza.getFechaFinVigencia(), poliza.getMesesVigenciaInicial());
        for (Riesgo riesgo : poliza.getRiesgos()) {
            RiesgoJpaEntity current = existing.get(riesgo.getId());
            if (current == null) {
                entity.agregarRiesgo(new RiesgoJpaEntity(riesgo.getId(), riesgo.getEstado(), entity));
            } else {
                current.actualizarEstado(riesgo.getEstado());
            }
        }
    }

    public Poliza toDomain(PolizaJpaEntity entity) {
        return Poliza.rehidratar(entity.getId(), entity.getTipo(), entity.getEstado(),
                entity.getFechaInicioVigencia(), entity.getFechaFinVigencia(), entity.getMesesVigenciaInicial(),
                entity.getCanonMensual(), entity.getRiesgos().stream()
                        .map(riesgo -> Riesgo.rehidratar(riesgo.getId(), riesgo.getEstado())).toList(),
                entity.getVersion());
    }
}
