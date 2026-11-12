package gestion_polizas.infrastructure.adapters.output.persistence;

import gestion_polizas.domain.enums.EstadoPoliza;
import gestion_polizas.domain.enums.TipoPoliza;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataPolizaRepository extends JpaRepository<PolizaJpaEntity, Long> {
    @Query("select distinct p from PolizaJpaEntity p left join fetch p.riesgos where p.id = :id")
    Optional<PolizaJpaEntity> buscarConRiesgos(@Param("id") Long id);

    // The subquery finds the owner; the fetch loads ALL of its risks, not just the matched one.
    @Query("""
            select distinct p from PolizaJpaEntity p left join fetch p.riesgos
            where p.id = (select r.poliza.id from RiesgoJpaEntity r where r.id = :riesgoId)
            """)
    Optional<PolizaJpaEntity> buscarPorRiesgo(@Param("riesgoId") Long riesgoId);

    @Query("""
            select distinct p from PolizaJpaEntity p left join fetch p.riesgos
            where (:tipo is null or p.tipo = :tipo) and (:estado is null or p.estado = :estado)
            order by p.id
            """)
    List<PolizaJpaEntity> listarConRiesgos(@Param("tipo") TipoPoliza tipo,
            @Param("estado") EstadoPoliza estado);

    @Query("""
            select count(p) from PolizaJpaEntity p
            where (:tipo is null or p.tipo = :tipo) and (:estado is null or p.estado = :estado)
            """)
    long contar(@Param("tipo") TipoPoliza tipo, @Param("estado") EstadoPoliza estado);

    @Query("""
            select p.id from PolizaJpaEntity p
            where (:tipo is null or p.tipo = :tipo) and (:estado is null or p.estado = :estado)
            order by p.id
            """)
    List<Long> listarIds(@Param("tipo") TipoPoliza tipo, @Param("estado") EstadoPoliza estado, Pageable pageable);

    @Query("""
            select distinct p from PolizaJpaEntity p left join fetch p.riesgos
            where p.id in :ids order by p.id
            """)
    List<PolizaJpaEntity> buscarPaginaConRiesgos(@Param("ids") List<Long> ids);

    @Query("select r.id from RiesgoJpaEntity r where r.id in :ids and r.poliza.id <> :polizaId")
    List<Long> buscarRiesgosDeOtraPoliza(@Param("ids") List<Long> ids, @Param("polizaId") Long polizaId);
}
