package gestion_polizas.infrastructure;

import gestion_polizas.domain.exceptions.BusinessConflictException;
import gestion_polizas.domain.exceptions.ResourceNotFoundException;
import gestion_polizas.infrastructure.adapters.input.rest.responses.ApiResponse;
import gestion_polizas.infrastructure.adapters.input.rest.responses.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.security.api-key=test-key")
@AutoConfigureMockMvc
@Import(HttpFoundationTests.ProbeController.class)
class HttpFoundationTests {
    @Autowired
    MockMvc mvc;

    @Test
    void missingKeyIsUnauthorized() throws Exception {
        error(get("/__test/probe"), 401, "API key ausente o incorrecta");
    }

    @Test
    void incorrectKeyIsUnauthorized() throws Exception {
        error(get("/__test/probe").header("x-api-key", "wrong"), 401, "API key ausente o incorrecta");
    }

    @Test
    void validKeyReturnsSuccessContractWithoutSession() throws Exception {
        var result = mvc.perform(authorized(get("/__test/probe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Operacion realizada correctamente"))
                .andExpect(jsonPath("$.body.value").value("ok"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void postWithValidKeyDoesNotRequireCsrfToken() throws Exception {
        mvc.perform(authorized(post("/__test/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"ok\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.body.value").value("ok"));
    }

    @Test
    void invalidBodyReturns400() throws Exception {
        error(authorized(post("/__test/probe").contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"\"}")), 400, "Los datos de la solicitud no son validos");
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        error(authorized(post("/__test/probe").contentType(MediaType.APPLICATION_JSON)
                .content("{")), 400, "Solicitud invalida");
    }

    @Test
    void invalidParameterTypeReturns400() throws Exception {
        error(authorized(get("/__test/probe/parameter").param("value", "abc")),
                400, "Solicitud invalida");
    }

    @Test
    void missingParameterReturns400() throws Exception {
        error(authorized(get("/__test/probe/parameter")), 400, "Solicitud invalida");
    }

    @Test
    void parameterConstraintReturns400() throws Exception {
        error(authorized(get("/__test/probe/parameter").param("value", "0")),
                400, "Los datos de la solicitud no son validos");
    }

    @Test
    void domainResourceNotFoundReturns404() throws Exception {
        error(authorized(get("/__test/probe/error/missing")), 404, "Recurso no encontrado");
    }

    @Test
    void unknownRouteReturns404() throws Exception {
        error(authorized(get("/does-not-exist")), 404, "Recurso no encontrado");
    }

    @Test
    void conflictReturns409() throws Exception {
        error(authorized(get("/__test/probe/error/conflict")),
                409, "La operacion presenta un conflicto de negocio");
    }

    @Test
    void unexpectedErrorDoesNotExposeInternalMessage() throws Exception {
        error(authorized(get("/__test/probe/error/internal")), 500, "Error interno del servidor");
    }

    @Test
    void unsupportedMethodPreserves405() throws Exception {
        error(authorized(delete("/__test/probe")), 405, "Solicitud invalida");
    }

    @Test
    void swaggerPathsAreAccessibleWithoutKey() throws Exception {
        // No springdoc dependency yet: these test-only handlers verify the allowlist.
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void h2PathIsProtectedOutsideDevelopment() throws Exception {
        error(get("/h2-console/"), 401, "API key ausente o incorrecta");
    }

    @Test
    void emptyPageAndNullBodyKeepContract() throws Exception {
        mvc.perform(authorized(get("/__test/probe/page")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.data").isEmpty())
                .andExpect(jsonPath("$.body.currentPage").value(1))
                .andExpect(jsonPath("$.body.lastPage").value(0))
                .andExpect(jsonPath("$.body.totalRecords").value(0))
                .andExpect(jsonPath("$.body.hasMorePages").value(false));
        mvc.perform(authorized(get("/__test/probe/empty")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"success\":true,\"message\":\"Sin datos\",\"body\":null}"));
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.header("x-api-key", "test-key");
    }

    private void error(MockHttpServletRequestBuilder request, int code, String message) throws Exception {
        mvc.perform(request)
                .andExpect(status().is(code))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"success\":false,\"message\":\"" + message + "\",\"body\":null}"))
                .andExpect(jsonPath("$.body").value(nullValue()))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    // Imported explicitly; never registered in the production application.
    @RestController
    @org.springframework.boot.test.context.TestComponent
    static class ProbeController {
        record Payload(@NotBlank String value) {}

        @GetMapping("/__test/probe")
        ApiResponse<Payload> probe() {
            return ApiResponse.success(new Payload("ok"));
        }

        @PostMapping("/__test/probe")
        ResponseEntity<ApiResponse<Payload>> validate(@Valid @RequestBody Payload payload) {
            return ResponseEntity.status(201).body(ApiResponse.success(payload));
        }

        @GetMapping("/__test/probe/parameter")
        ApiResponse<Integer> parameter(@RequestParam @Min(1) int value) {
            return ApiResponse.success(value);
        }

        @GetMapping("/__test/probe/error/{kind}")
        ApiResponse<Void> fail(@PathVariable String kind) {
            throw switch (kind) {
                case "missing" -> new ResourceNotFoundException("internal resource details");
                case "conflict" -> new BusinessConflictException("internal conflict details");
                default -> new IllegalStateException("internal sensitive details");
            };
        }

        @GetMapping("/__test/probe/page")
        ApiResponse<PageResponse<String>> page() {
            return ApiResponse.success(new PageResponse<>(List.of(), 1, 0, 0, false));
        }

        @GetMapping("/__test/probe/empty")
        ApiResponse<Void> empty() {
            return ApiResponse.success("Sin datos", null);
        }

        @GetMapping({"/swagger-ui/index.html", "/v3/api-docs"})
        ApiResponse<Void> docs() {
            return ApiResponse.success(null);
        }
    }
}
