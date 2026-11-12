package gestion_polizas.infrastructure.adapters.output.persistence;

import gestion_polizas.domain.enums.EstadoPoliza;
import gestion_polizas.domain.enums.TipoPoliza;
import gestion_polizas.domain.exceptions.BusinessConflictException;
import gestion_polizas.domain.exceptions.ConcurrentPolizaModificationException;
import gestion_polizas.domain.model.Poliza;
import gestion_polizas.domain.model.Riesgo;
import gestion_polizas.domain.ports.out.PolizaRepositoryPort;
import gestion_polizas.domain.ports.out.PolizaPage;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class PolizaPersistenceAdapter implements PolizaRepositoryPort {
    private final SpringDataPolizaRepository repository;
    private final PolizaPersistenceMapper mapper;

    public PolizaPersistenceAdapter(SpringDataPolizaRepository repository, PolizaPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Poliza> buscarPorId(Long id) {
        return repository.buscarConRiesgos(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Poliza> buscarPorRiesgoId(Long riesgoId) {
        return repository.buscarPorRiesgo(riesgoId).map(mapper::toDomain);
    }

    @Override
    public List<Poliza> listar(TipoPoliza tipo, EstadoPoliza estado) {
        return repository.listarConRiesgos(tipo, estado).stream().map(mapper::toDomain).toList();
    }

    @Override
    public PolizaPage listar(TipoPoliza tipo, EstadoPoliza estado, int pageIndex, int size) {
        long total = repository.contar(tipo, estado);
        // Avoid overflowing JPA's offset for very large page numbers on small result sets.
        if ((long) pageIndex * size >= total) {
            return new PolizaPage(List.of(), total);
        }
        List<Long> ids = repository.listarIds(tipo, estado, PageRequest.of(pageIndex, size));
        if (ids.isEmpty()) {
            return new PolizaPage(List.of(), total);
        }
        return new PolizaPage(repository.buscarPaginaConRiesgos(ids).stream().map(mapper::toDomain).toList(), total);
    }

    @Override
    @Transactional
    public Poliza guardar(Poliza poliza) {
        List<Long> ids = poliza.getRiesgos().stream().map(Riesgo::getId).toList();
        if (!repository.buscarRiesgosDeOtraPoliza(ids, poliza.getId()).isEmpty()) {
            throw new BusinessConflictException("Un riesgo ya pertenece a otra poliza");
        }
        Optional<PolizaJpaEntity> existing = repository.buscarConRiesgos(poliza.getId());
        PolizaJpaEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            if (!Objects.equals(poliza.getRevision(), entity.getVersion())) {
                throw new ConcurrentPolizaModificationException();
            }
            mapper.actualizar(poliza, entity);
            entity.marcarActualizacion();
            try {
                repository.flush();
            } catch (OptimisticLockingFailureException ex) {
                throw new ConcurrentPolizaModificationException(ex);
            }
        } else {
            if (poliza.getRevision() != null) {
                throw new ConcurrentPolizaModificationException();
            }
            // Null @Version selects persist rather than merge for assigned IDs.
            entity = repository.saveAndFlush(mapper.toEntity(poliza));
        }
        return mapper.toDomain(entity);
    }
}
