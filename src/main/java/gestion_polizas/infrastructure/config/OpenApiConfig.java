package gestion_polizas.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {
    @Bean
    OpenAPI polizasOpenApi() {
        var error = new ObjectSchema()
                .addProperty("success", new BooleanSchema().example(false))
                .addProperty("message", new StringSchema())
                .addProperty("body", new ObjectSchema().nullable(true));
        error.setRequired(java.util.List.of("success", "message", "body"));
        return new OpenAPI().info(new Info().title("Gestión de pólizas de arrendamiento").version("1.0")
                .description("API de la prueba técnica. Todas las operaciones requieren x-api-key; CORE mock solo registra intentos."))
                .components(new Components().addSecuritySchemes("ApiKey", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("x-api-key"))
                        .addSchemas("ApiError", error))
                .addSecurityItem(new SecurityRequirement().addList("ApiKey"));
    }

    @Bean
    OpenApiCustomizer errorResponses() {
        return api -> api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            var responses = operation.getResponses();
            responses.addApiResponse("400", error("Solicitud inválida"));
            responses.addApiResponse("401", error("API key ausente o incorrecta"));
            responses.addApiResponse("500", error("Error interno del servidor"));
            if (path.contains("{id}")) responses.addApiResponse("404", error("Recurso no encontrado"));
            if (method == io.swagger.v3.oas.models.PathItem.HttpMethod.POST && !path.startsWith("/core-mock")) {
                responses.addApiResponse("409", error("Conflicto de negocio o concurrencia"));
                responses.addApiResponse("502", error("No fue posible notificar al CORE"));
            }
        }));
    }

    private ApiResponse error(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
                new MediaType().schema(new ObjectSchema().$ref("#/components/schemas/ApiError"))));
    }
}
