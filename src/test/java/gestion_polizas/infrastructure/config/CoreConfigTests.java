package gestion_polizas.infrastructure.config;

import gestion_polizas.domain.ports.out.CoreNotificationPort;
import gestion_polizas.infrastructure.adapters.output.core.CoreHttpAdapter;
import gestion_polizas.infrastructure.adapters.output.core.FakeCoreAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CoreConfigTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(CoreConfig.class)
            .withPropertyValues("app.security.api-key=test-core-key", "app.core.mode=fake",
                    "app.core.base-url=http://127.0.0.1:12345", "app.core.connect-timeout=1s", "app.core.response-timeout=2s");

    @Test
    void modoFakeRegistraUnSoloAdaptador() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(CoreNotificationPort.class);
            assertThat(context.getBean(CoreNotificationPort.class)).isInstanceOf(FakeCoreAdapter.class);
        });
    }

    @Test
    void modoHttpRegistraUnSoloAdaptador() {
        runner.withPropertyValues("app.core.mode=http").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(CoreNotificationPort.class);
            assertThat(context.getBean(CoreNotificationPort.class)).isInstanceOf(CoreHttpAdapter.class);
        });
    }

    @Test
    void adaptadorDePruebaSustituyeAlPredeterminado() {
        CoreNotificationPort custom = id -> {};
        runner.withBean(CoreNotificationPort.class, () -> custom).run(context -> {
            assertThat(context).hasSingleBean(CoreNotificationPort.class);
            assertThat(context.getBean(CoreNotificationPort.class)).isSameAs(custom);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"app.core.mode=otro", "app.core.base-url=ftp://localhost",
            "app.core.base-url=/relativa", "app.core.base-url=http://user:secret@localhost",
            "app.core.base-url=http://localhost?x=1", "app.core.connect-timeout=0ms",
            "app.core.response-timeout=-1s", "app.core.connect-timeout=abc"})
    void configuracionInvalidaImpideArranque(String property) {
        runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }
}
