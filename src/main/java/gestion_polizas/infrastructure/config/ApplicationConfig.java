package gestion_polizas.infrastructure.config;

import gestion_polizas.application.usecases.GestionPolizasService;
import gestion_polizas.domain.ports.out.CoreNotificationPort;
import gestion_polizas.domain.ports.out.PolizaRepositoryPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RenovacionProperties.class)
public class ApplicationConfig {
    @Bean
    GestionPolizasService gestionPolizasService(PolizaRepositoryPort repository, CoreNotificationPort core,
            RenovacionProperties properties) {
        return new GestionPolizasService(repository, core, properties.ipcPorcentaje());
    }

}
