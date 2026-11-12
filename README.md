# Gestión de pólizas de arrendamiento

Base técnica de la prueba con Java 21, Spring Boot 4.1.2 y Maven. Este incremento contiene contratos HTTP, manejo de errores y seguridad con API key. No contiene entidades Póliza/Riesgo, consultas paginadas, endpoints de negocio ni integración CORE.

## Requisitos y ejecución

- JDK 21 disponible en `JAVA_HOME` y `PATH`.
- Maven Wrapper incluido (Maven 3.10.0); no se requiere instalar Maven.
- Acceso a Maven Central para la primera descarga de dependencias.

En PowerShell, desde la raíz:

```powershell
# Desarrollo: habilita H2 Console y la clave de prueba predeterminada.
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"

# Pruebas automatizadas.
.\mvnw.cmd test

# Empaquetar.
.\mvnw.cmd package

# Ejecutar el jar en desarrollo.
java -jar target/gestion-polizas-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

En Linux/macOS usar `./mvnw` en lugar de `.\mvnw.cmd`. El puerto predeterminado es `8080` (configurable con `SERVER_PORT`).

Sin el perfil `dev`, configurar una clave no vacía:

```powershell
$env:API_KEY = '<clave-del-entorno>'
.\mvnw.cmd spring-boot:run
```

Sin clave configurada, el arranque falla por validación de configuración. La misma variable permite sobrescribir la clave en desarrollo. Esta base usa H2 en memoria incluso fuera de `dev`; no constituye una configuración de despliegue productivo.

## Seguridad

- Header obligatorio: `x-api-key`. En `dev`, su valor predeterminado es `123456`.
- Clave ausente o incorrecta: HTTP **401** con `ApiResponse`, sin redirección a login.
- Spring Security protege por defecto todas las rutas, incluidas las futuras rutas de pólizas, riesgos y mock CORE, excepto las rutas públicas indicadas abajo.
- Acceso público a `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs` y `/v3/api-docs/**`.
- Swagger/OpenAPI todavía no está instalado; solo se configuró su acceso público. Estas rutas aún no ofrecen documentación.
- `/h2-console` y `/h2-console/**` son públicos únicamente en `dev`. La consola está deshabilitada por defecto. En desarrollo usa URL JDBC `jdbc:h2:mem:polizasdb`, usuario `sa` y contraseña vacía.
- No hay usuarios, JWT, HTTP Basic, formulario de login ni sesiones de autenticación. Se excluye la autoconfiguración del usuario de seguridad predeterminado.
- CSRF está deshabilitado porque la autenticación usa un header explícito y no cookies. El filtro vive exclusivamente dentro de Spring Security, sin registro Servlet duplicado. La API key no se guarda como credencial en el contexto ni se registra en logs.
- La excepción de frames `SAMEORIGIN` se limita a la cadena de H2; el resto conserva la protección predeterminada.

Ejemplos para comprobar el rechazo, sin endpoints de negocio todavía:

```powershell
curl.exe -i http://localhost:8080/polizas
# 401: no se envió la clave.

curl.exe -i -H "x-api-key: incorrecta" http://localhost:8080/polizas
# 401: clave incorrecta.

curl.exe -i -H "x-api-key: 123456" http://localhost:8080/polizas
# En dev: 404, porque /polizas aún no existe; la autenticación fue aceptada.
```

## Arquitectura

Se conserva el paquete raíz `gestion_polizas`:

- `domain/exceptions`: excepciones independientes de Spring y HTTP para recursos inexistentes y conflictos de negocio.
- `infrastructure/adapters/input/rest/responses`: records de respuesta HTTP.
- `infrastructure/exception`: traducción centralizada de excepciones a HTTP mediante `@RestControllerAdvice`.
- `infrastructure/config`: propiedades de configuración validadas.
- `infrastructure/security`: cadenas de Spring Security, filtro de API key y respuestas de errores de seguridad.

Los futuros modelos, enums y puertos estarán en `domain`; los casos de uso y DTOs de aplicación en `application`; JPA y CORE en adaptadores de salida de `infrastructure`. Se crearán cuando exista funcionalidad concreta, sin capas vacías. El dominio no dependerá de DTOs HTTP, JPA ni anotaciones Spring.

## Respuestas y errores

`ApiResponse<T>` es un record con métodos estáticos `success(body)`, `success(message, body)` y `error(message)`:

```json
{"success":true,"message":"Operacion realizada correctamente","body":{}}
```

Los errores conservan el mismo contrato:

```json
{"success":false,"message":"API key ausente o incorrecta","body":null}
```

`ResponseEntity` determina el código HTTP en los manejadores y futuros controladores. El manejador global cubre validación de cuerpos/parámetros, JSON mal formado, parámetros ausentes o de tipo incorrecto (**400**), recursos inexistentes (**404**), conflictos de dominio (**409**) y fallos inesperados (**500**). Preserva además códigos MVC como **405** y **415**. Los fallos internos se registran en el servidor; sus mensajes y trazas no se devuelven al cliente. Los errores de seguridad se escriben desde la cadena de filtros, ya que ocurren antes del controlador; usan el mismo record (401 y 403).

## Paginación

`PageResponse<T>` define el futuro contenido de `body`:

```json
{"data":[],"currentPage":1,"lastPage":0,"totalRecords":0,"hasMorePages":false}
```

- Página inicial **1**, tamaño predeterminado **20**, máximo **100** (constantes del record).
- Conjunto vacío: `lastPage=0`, `totalRecords=0` y `hasMorePages=false`.
- Al implementar consultas se validarán página/tamaño, se convertirá el índice a base 0 de Spring Data y se aplicará un orden estable por `id`.
- Este incremento solo define el contrato; todavía no aplica límites a solicitudes ni implementa consultas o conversiones JPA.

## Pruebas y alcance pendiente

Las pruebas cubren factories y serialización JSON, página vacía, validaciones HTTP, errores 400/404/405/409/500, API key ausente/incorrecta/válida, ausencia de sesión y POST sin token CSRF. Los handlers usados para pruebas HTTP viven solo en `src/test`, se importan explícitamente y se excluyen del escaneo normal. No son endpoints del producto. Las rutas Swagger simuladas en esos tests verifican exclusivamente la configuración de acceso público.

También se prueba el perfil `dev` con servidor embebido y puerto aleatorio: acceso real a H2 sin API key y autenticación HTTP. El contexto sin perfil comprueba que H2 y los controladores de prueba no estén registrados.

H2 y JPA ya existían; se conserva `ddl-auto=update`. Se movieron consola y SQL visible al perfil `dev`. Las advertencias de instrumentación dinámica de Mockito bajo Java 21 provienen de las dependencias existentes de pruebas.

Pendientes para incrementos posteriores: confirmar IPC/fórmula de prima, reglas ambiguas de renovación/cancelación y consistencia frente a fallos del CORE; implementar dominio, persistencia, casos de uso, endpoints, mock CORE y OpenAPI. Distinguir el diseño teórico del módulo 1 de la implementación mínima del módulo 2. No se agregaron JWT, Lombok, MapStruct ni dependencias de cliente HTTP todavía.
