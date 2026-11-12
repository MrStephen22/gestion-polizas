package gestion_polizas.domain.model;

import gestion_polizas.domain.enums.EstadoRiesgo;
import gestion_polizas.domain.exceptions.InvalidDomainDataException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiesgoTests {
    @Test
    void crearRiesgoActivoConIdentidad() {
        Riesgo riesgo = new Riesgo(10L);
        assertThat(riesgo.getId()).isEqualTo(10L);
        assertThat(riesgo.getEstado()).isEqualTo(EstadoRiesgo.ACTIVO);
    }

    @Test
    void rechazarRiesgoSinIdentidad() {
        assertThatThrownBy(() -> new Riesgo(null)).isInstanceOf(InvalidDomainDataException.class);
    }
}
