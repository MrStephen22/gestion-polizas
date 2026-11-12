package gestion_polizas.infrastructure.adapters.input.rest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.security.api-key=core-test-key", "app.core.mode=fake", "app.dev-data.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:core-mock-docs;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class CoreMockAndOpenApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void recibirEventoYRegistrarIntentoSinRegistrarClave(CapturedOutput output) throws Exception {
        mvc.perform(post("/core-mock/evento").header("x-api-key", "core-test-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"evento\":\"ACTUALIZACION\",\"polizaId\":555}"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"success":true,"message":"Operacion realizada correctamente",
                         "body":{"evento":"ACTUALIZACION","polizaId":555}}
                        """));
        assertThat(output).contains("Intento CORE evento=ACTUALIZACION polizaId=555").doesNotContain("core-test-key");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{", "{\"evento\":null,\"polizaId\":555}",
            "{\"evento\":\"OTRO\",\"polizaId\":555}", "{\"evento\":\"\",\"polizaId\":555}",
            "{\"evento\":\"ACTUALIZACION\",\"polizaId\":null}",
            "{\"evento\":\"ACTUALIZACION\",\"polizaId\":0}",
            "{\"evento\":\"ACTUALIZACION\",\"polizaId\":-1}",
            "{\"evento\":\"ACTUALIZACION\",\"polizaId\":1.5}"})
    void validarMockSinRegistrarEventosInvalidos(String body, CapturedOutput output) throws Exception {
        mvc.perform(post("/core-mock/evento").header("x-api-key", "core-test-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.body").value(nullValue()));
        assertThat(output).doesNotContain("Intento CORE evento=");
    }

    @Test
    void mockExigeMismaClaveQueNegocio() throws Exception {
        mvc.perform(post("/core-mock/evento").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evento\":\"ACTUALIZACION\",\"polizaId\":555}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(post("/core-mock/evento").header("x-api-key", "incorrecta").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evento\":\"ACTUALIZACION\",\"polizaId\":555}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.body").value(nullValue()));
    }

    @Test
    void openApiDocumentaOperacionesEsquemasParametrosYApiKeySinExigirClave() throws Exception {
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.ApiKey.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.ApiKey.in").value("header"))
                .andExpect(jsonPath("$.components.securitySchemes.ApiKey.name").value("x-api-key"))
                .andExpect(jsonPath("$.security[0].ApiKey").isArray())
                .andExpect(jsonPath("$['paths']['/polizas']['get']['responses']['200']['content']['application/json']['schema']").exists())
                .andExpect(jsonPath("$['paths']['/polizas/{id}/riesgos']['get']").exists())
                .andExpect(jsonPath("$['paths']['/polizas/{id}/riesgos']['post']['responses']['201']").exists())
                .andExpect(jsonPath("$['paths']['/polizas/{id}/renovar']['post']['responses']['502']").exists())
                .andExpect(jsonPath("$['paths']['/polizas/{id}/cancelar']['post']['responses']['409']").exists())
                .andExpect(jsonPath("$['paths']['/riesgos/{id}/cancelar']['post']").exists())
                .andExpect(jsonPath("$['paths']['/core-mock/evento']['post']['requestBody']").exists())
                .andExpect(jsonPath("$['components']['schemas']['CoreEventRequest']['properties']['evento']['enum']", hasItem("ACTUALIZACION")))
                .andExpect(jsonPath("$['paths']['/core-mock/evento']['post']['responses']['401']").exists())
                .andReturn();
        var parameters = mapper.readTree(result.getResponse().getContentAsString())
                .path("paths").path("/polizas").path("get").path("parameters");
        assertThat(parameters.size()).isEqualTo(4);
        parameters.forEach(parameter -> {
            if (parameter.path("name").asText().equals("size")) {
                assertThat(parameter.path("schema").path("maximum").asInt()).isEqualTo(100);
                assertThat(parameter.path("schema").path("default").asInt()).isEqualTo(20);
            }
        });
    }

    @Test
    void swaggerUiYSuConfiguracionEstanDisponibles() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/swagger-ui/index.html")));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));
    }
}
