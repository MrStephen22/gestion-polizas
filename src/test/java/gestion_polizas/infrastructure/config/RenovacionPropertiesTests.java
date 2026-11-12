package gestion_polizas.infrastructure.config;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class RenovacionPropertiesTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @ParameterizedTest
    @ValueSource(strings = {"0", "5", "5.25"})
    void aceptarPorcentajesNoNegativos(String value) {
        runner.withPropertyValues("app.renovacion.ipc-porcentaje=" + value).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RenovacionProperties.class).ipcPorcentaje())
                    .isEqualByComparingTo(new BigDecimal(value));
        });
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"-1", "abc", ""})
    void configuracionInvalidaImpideArranque(String value) {
        var configured = value == null ? runner : runner.withPropertyValues("app.renovacion.ipc-porcentaje=" + value);
        configured.run(context -> assertThat(context).hasFailed());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RenovacionProperties.class)
    static class PropertiesConfig {}
}
