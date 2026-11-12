package gestion_polizas.application.usecases;

import gestion_polizas.application.dto.PolizaDto;
import gestion_polizas.application.dto.ListadoPolizasDto;
import gestion_polizas.application.dto.RiesgoDto;
import gestion_polizas.domain.enums.EstadoPoliza;
import gestion_polizas.domain.enums.TipoPoliza;
import gestion_polizas.domain.exceptions.ResourceNotFoundException;
import gestion_polizas.domain.model.Poliza;
import gestion_polizas.domain.model.Riesgo;
import gestion_polizas.domain.ports.out.CoreNotificationPort;
import gestion_polizas.domain.ports.out.PolizaRepositoryPort;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public class GestionPolizasService {
    private final PolizaRepositoryPort repository;
    private final CoreNotificationPort core;
    private final BigDecimal ipcPorcentaje;

    public GestionPolizasService(PolizaRepositoryPort repository, CoreNotificationPort core,
            BigDecimal ipcPorcentaje) {
        this.repository = repository;
        this.core = core;
        this.ipcPorcentaje = ipcPorcentaje;
    }

    public List<PolizaDto> listar(TipoPoliza tipo, EstadoPoliza estado) {
        return repository.listar(tipo, estado).stream().map(PolizaDto::from).toList();
    }

    public ListadoPolizasDto listar(TipoPoliza tipo, EstadoPoliza estado, int page, int size) {
        var result = repository.listar(tipo, estado, page - 1, size);
        return new ListadoPolizasDto(result.data().stream().map(PolizaDto::from).toList(), result.totalRecords());
    }

    public List<RiesgoDto> consultarRiesgos(Long polizaId) {
        return buscarPoliza(polizaId).getRiesgos().stream().map(RiesgoDto::from).toList();
    }

    @Transactional
    public PolizaDto renovar(Long polizaId) {
        Poliza poliza = buscarPoliza(polizaId);
        poliza.renovar(ipcPorcentaje);
        return guardarYNotificar(poliza);
    }

    @Transactional
    public PolizaDto cancelar(Long polizaId) {
        Poliza poliza = buscarPoliza(polizaId);
        return poliza.cancelar() ? guardarYNotificar(poliza) : PolizaDto.from(poliza);
    }

    @Transactional
    public PolizaDto agregarRiesgo(Long polizaId, Long riesgoId) {
        Poliza poliza = buscarPoliza(polizaId);
        poliza.agregarRiesgo(new Riesgo(riesgoId));
        return guardarYNotificar(poliza);
    }

    @Transactional
    public PolizaDto cancelarRiesgo(Long riesgoId) {
        Poliza poliza = repository.buscarPorRiesgoId(riesgoId)
                .orElseThrow(() -> new ResourceNotFoundException("Riesgo no encontrado"));
        return poliza.cancelarRiesgo(riesgoId) ? guardarYNotificar(poliza) : PolizaDto.from(poliza);
    }

    private Poliza buscarPoliza(Long id) {
        return repository.buscarPorId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Poliza no encontrada"));
    }

    private PolizaDto guardarYNotificar(Poliza poliza) {
        Poliza saved = repository.guardar(poliza);
        core.notificarActualizacion(saved.getId());
        return PolizaDto.from(saved);
    }
}
