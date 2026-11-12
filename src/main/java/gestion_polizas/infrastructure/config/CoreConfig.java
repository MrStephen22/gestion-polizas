package gestion_polizas.infrastructure.config;

import gestion_polizas.domain.ports.out.CoreNotificationPort;
import gestion_polizas.infrastructure.adapters.output.core.CoreHttpAdapter;
import gestion_polizas.infrastructure.adapters.output.core.FakeCoreAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({CoreProperties.class, ApiKeyProperties.class})
public class CoreConfig {
    @Bean
    @ConditionalOnMissingBean(CoreNotificationPort.class)
    CoreNotificationPort coreNotificationPort(CoreProperties properties, ApiKeyProperties apiKey) {
        return switch (properties.mode()) {
            case FAKE -> new FakeCoreAdapter();
            case HTTP -> new CoreHttpAdapter(properties, apiKey.getApiKey());
        };
    }
}
