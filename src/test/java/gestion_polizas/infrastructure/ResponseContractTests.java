package gestion_polizas.infrastructure;

import gestion_polizas.infrastructure.adapters.input.rest.responses.ApiResponse;
import gestion_polizas.infrastructure.adapters.input.rest.responses.PageResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseContractTests {
    @Test
    void factoriesPreserveResponseData() {
        assertThat(ApiResponse.success("data"))
                .isEqualTo(new ApiResponse<>(true, "Operacion realizada correctamente", "data"));
        assertThat(ApiResponse.success("Creado", 42))
                .isEqualTo(new ApiResponse<>(true, "Creado", 42));
        assertThat(ApiResponse.error("Error"))
                .isEqualTo(new ApiResponse<>(false, "Error", null));
    }

    @Test
    void pageDataIsAnImmutableSnapshot() {
        var source = new ArrayList<>(List.of("a"));
        var page = new PageResponse<>(source, 1, 2, 21, true);
        source.clear();
        assertThat(page.data()).containsExactly("a");
        assertThatThrownBy(() -> page.data().add("b")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(PageResponse.FIRST_PAGE).isEqualTo(1);
        assertThat(PageResponse.DEFAULT_SIZE).isEqualTo(20);
        assertThat(PageResponse.MAX_SIZE).isEqualTo(100);
    }
}
