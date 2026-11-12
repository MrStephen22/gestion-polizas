package gestion_polizas.infrastructure.config;

import gestion_polizas.domain.enums.TipoPoliza;
import gestion_polizas.domain.model.Poliza;
import gestion_polizas.domain.model.Riesgo;
import gestion_polizas.domain.ports.out.PolizaRepositoryPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.dev-data.enabled", havingValue = "true")
public class DevelopmentDataInitializer implements ApplicationRunner {
    private final PolizaRepositoryPort repository;

    public DevelopmentDataInitializer(PolizaRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        insertarSiNoExiste(poliza(1L, TipoPoliza.INDIVIDUAL, "1000", 101L));
        insertarSiNoExiste(poliza(2L, TipoPoliza.COLECTIVA, "2000", 201L, 202L, 203L));
        Poliza renovada = poliza(3L, TipoPoliza.INDIVIDUAL, "1500", 301L);
        renovada.renovar(new BigDecimal("5")); // Fixed historical fixture; runtime renewals use configuration.
        insertarSiNoExiste(renovada);
        Poliza cancelada = poliza(4L, TipoPoliza.COLECTIVA, "3000", 401L, 402L);
        cancelada.cancelar();
        insertarSiNoExiste(cancelada);
    }

    private void insertarSiNoExiste(Poliza poliza) {
        if (repository.buscarPorId(poliza.getId()).isEmpty()) {
            repository.guardar(poliza);
        }
    }

    private Poliza poliza(Long id, TipoPoliza tipo, String canon, Long... riesgos) {
        return new Poliza(id, tipo, LocalDate.of(2026, 1, 1), 12, new BigDecimal(canon),
                java.util.Arrays.stream(riesgos).map(Riesgo::new).toList());
    }
}
