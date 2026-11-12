# Gestión de pólizas de arrendamiento

Prueba técnica con Java 21, Spring Boot 4.1.2 y Maven: seis endpoints de gestión, paginación, validación HTTP, API key, dominio, persistencia JPA/H2, casos de uso transaccionales, cliente HTTP CORE, endpoint mock y Swagger/OpenAPI. El adaptador CORE se selecciona por configuración (`fake` o `http`).

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
- Spring Security protege por defecto todas las rutas, incluidas pólizas, riesgos y `/core-mock/evento`, excepto las rutas públicas indicadas abajo. El enunciado exige el header obligatorio y no establece una excepción para el mock.
- Acceso público a `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs` y `/v3/api-docs/**`.
- Swagger UI está disponible en `/swagger-ui.html` y la especificación JSON en `/v3/api-docs`. La documentación es pública; ejecutar operaciones desde ella exige usar **Authorize** e ingresar la clave, sin prefijo `Bearer`.
- `/h2-console` y `/h2-console/**` son públicos únicamente en `dev`. La consola está deshabilitada por defecto. En desarrollo usa URL JDBC `jdbc:h2:mem:polizasdb`, usuario `sa` y contraseña vacía.
- No hay usuarios, JWT, HTTP Basic, formulario de login ni sesiones de autenticación. Se excluye la autoconfiguración del usuario de seguridad predeterminado.
- CSRF está deshabilitado porque la autenticación usa un header explícito y no cookies. El filtro vive exclusivamente dentro de Spring Security, sin registro Servlet duplicado. La API key no se guarda como credencial en el contexto ni se registra en logs.
- La excepción de frames `SAMEORIGIN` se limita a la cadena de H2; el resto conserva la protección predeterminada.

Ejemplos para comprobar el acceso:

```powershell
curl.exe -i http://localhost:8080/polizas
# 401: no se envió la clave.

curl.exe -i -H "x-api-key: incorrecta" http://localhost:8080/polizas
# 401: clave incorrecta.

curl.exe -i -H "x-api-key: 123456" http://localhost:8080/polizas
# En dev: 200, listado paginado con las pólizas de ejemplo.
```

## Arquitectura

Se conserva el paquete raíz `gestion_polizas`:

- `domain/exceptions`: excepciones independientes de Spring y HTTP para recursos inexistentes y conflictos de negocio.
- `infrastructure/adapters/input/rest/responses`: records de respuesta HTTP.
- `infrastructure/exception`: traducción centralizada de excepciones a HTTP mediante `@RestControllerAdvice`.
- `infrastructure/config`: propiedades de configuración validadas.
- `infrastructure/security`: cadenas de Spring Security, filtro de API key y respuestas de errores de seguridad.

Los modelos, enums y puertos están en `domain`; los casos de uso y DTOs están en `application`. JPA está en `infrastructure/adapters/output/persistence`; `FakeCoreAdapter` y `CoreHttpAdapter` están en `infrastructure/adapters/output/core`. `ApplicationConfig` construye el servicio y `CoreConfig` selecciona el adaptador mediante inyección por constructor y propiedades validadas. La aplicación no depende de infraestructura. El dominio no depende de DTOs HTTP, JPA ni anotaciones Spring.

## Dominio y persistencia

`Poliza` controla sus riesgos inmutables. Las individuales tienen exactamente uno y las colectivas al menos uno al crearse. Solo las colectivas no canceladas admiten nuevos riesgos. Las cancelaciones son idempotentes y conservan los registros.

La prima se calcula como canon mensual por meses de vigencia inicial. El IPC se recibe como porcentaje no negativo (5 significa 5 %), sin valor predeterminado en el dominio. Los importes usan `BigDecimal`, escala 2 y `HALF_UP`. Cada renovación ajusta el canon y abre el siguiente periodo de igual duración, sin reactivar riesgos cancelados. La vigencia usa `LocalDate`, inicio incluido y fin excluido, con aritmética `plusMonths` para finales de mes y años bisiestos.

El adaptador implementa `PolizaRepositoryPort`:

- Tablas `polizas` y `riesgos`, identificadores asignados por el dominio y estados/tipos como texto. `riesgos.id` es clave primaria global y `poliza_id` es una clave foránea obligatoria, no actualizable mediante JPA.
- El canon se guarda en `DECIMAL(38,2)`; importes que excedan esa capacidad son una limitación de almacenamiento. La prima es derivada también en la entidad (`@Transient`), evitando una columna redundante que pueda quedar inconsistente.
- La relación padre-hijo conserva el orden mediante `posicion`. No hay borrado de huérfanos ni cascada de eliminación. La cancelación actualiza estados y no borra filas.
- Las actualizaciones trabajan sobre entidades gestionadas, conservan riesgos existentes y agregan nuevos. Se rechazan riesgos de otra póliza y snapshots que omitan riesgos ya persistidos, para evitar reasignaciones o pérdida de registros. La clave primaria protege también frente a inserciones concurrentes duplicadas.
- Se usa `@Version` en la entidad de póliza y se transporta su valor como `revision` en el agregado y el DTO de aplicación, sin anotaciones JPA en el dominio. Antes de actualizar se compara la revisión leída con la persistida; una revisión obsoleta o ausente no puede sobrescribir una póliza existente. El adaptador devuelve un nuevo snapshot con la revisión actualizada: debe usarse ese resultado en posteriores escrituras. Un agregado nuevo tiene revisión nula.
- La columna técnica `revision_contenido` se incrementa en cada actualización para ensuciar la raíz incluso cuando solo cambia un riesgo. De esta forma Hibernate comprueba e incrementa `@Version` durante el `flush`, también para cambios exclusivos de hijos. Dos escrituras simultáneas con la misma versión producen un único ganador; la otra lanza `ConcurrentPolizaModificationException`, un conflicto de dominio. No hay reintentos automáticos.
- Las lecturas por ID, por riesgo y por filtros cargan todos los riesgos mediante `JOIN FETCH`, sin N+1. Al buscar por riesgo, una subconsulta localiza la póliza y el fetch recupera todos sus riesgos. Los listados se ordenan por ID de póliza.
- Los casos de uso agrupan búsqueda, operación de dominio, guardado y notificación en una transacción local. Las lecturas usan `readOnly=true`. Los objetos de dominio devueltos no dependen de una sesión JPA abierta.

Las fábricas `Poliza.rehidratar` y `Riesgo.rehidratar` restauran el estado sin setters públicos y sin ejecutar de nuevo renovaciones/cancelaciones. Reutilizan las invariantes de creación; verifican estado, periodo y que una póliza cancelada no tenga riesgos activos. La prima siempre se vuelve a derivar.

Las pruebas de persistencia usan H2 con esquema `create-drop`, transacciones revertidas por prueba y datos propios. Vacían el contexto JPA antes de leer para comprobar la reconstrucción real desde la base. Las estadísticas de Hibernate verifican una sola sentencia SQL para cada lectura no paginada con riesgos. También se prueban claves primarias, claves foráneas, renovaciones, cancelaciones, filtros y rechazo de datos inconsistentes.

## Casos de uso, IPC y notificaciones CORE

`GestionPolizasService` coordina seis operaciones y devuelve records de `application/dto`, independientes de HTTP y JPA:

| Método | Resultado |
| --- | --- |
| `listar(tipo, estado)` | Pólizas filtradas; `null` significa sin filtro. |
| `listar(tipo, estado, page, size)` | Página de pólizas y total de registros para el adaptador HTTP. |
| `consultarRiesgos(polizaId)` | Riesgos activos y cancelados de la póliza. |
| `renovar(polizaId)` | Póliza renovada con el IPC configurado. |
| `cancelar(polizaId)` | Póliza y todos sus riesgos cancelados. |
| `agregarRiesgo(polizaId, riesgoId)` | Póliza con el nuevo riesgo. Por ahora se recibe su identificador explícitamente. |
| `cancelarRiesgo(riesgoId)` | Póliza propietaria con el riesgo cancelado. |

Las reglas se ejecutan en el dominio. Recursos inexistentes producen `ResourceNotFoundException`. Una cancelación repetida devuelve el estado existente, sin guardar, incrementar revisión ni notificar. Las consultas tampoco notifican. Una renovación con IPC cero sí cambia el periodo y se notifica.

La propiedad `app.renovacion.ipc-porcentaje` usa `${IPC_PORCENTAJE:5}`: el valor predeterminado es **5 %**, también en desarrollo. Se valida al arrancar: debe existir, ser numérico y no negativo; cero es válido. No se codifica el IPC en `Poliza`. Ejemplo en PowerShell:

```powershell
$env:IPC_PORCENTAJE = '5.25'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

`CoreNotificationPort` expresa éxito por retorno normal y fallo mediante `CoreNotificationException` (no comprobada). `CoreConfig` crea un único adaptador según `app.core.mode`. Si se configura un bean del puerto para pruebas, no registra otro adaptador predeterminado. En pruebas también se usa un fake controlable que registra intentos y puede fallar.

Cada modificación efectiva guarda y hace `flush` antes de notificar una vez. Un fallo CORE se propaga y revierte todos los cambios locales, incluida la revisión. Un fallo de persistencia evita la notificación. Las pruebas de aplicación con H2 no tienen una transacción envolvente de prueba: verifican commits y rollbacks reales, con datos reproducibles. También se prueban snapshots obsoletos y dos transacciones concurrentes sobre distintos riesgos de una misma póliza.

**Límite de consistencia:** `@Transactional` no hace atómica la base de datos con el CORE. El CORE podría aceptar la operación y perderse la respuesta, o el commit local fallar después de notificar. No se implementan reintentos, Outbox, mensajería ni compensaciones. El mock registra intentos, no demuestra actualización de un CORE legado ni entrega exactamente una vez.

## Configuración del cliente HTTP CORE

| Propiedad | Variable de entorno | Valor predeterminado |
| --- | --- | --- |
| `app.core.mode` | `CORE_MODE` | `fake` |
| `app.core.base-url` | `CORE_BASE_URL` | `http://localhost:${server.port}` |
| `app.core.connect-timeout` | `CORE_CONNECT_TIMEOUT` | `1s` |
| `app.core.response-timeout` | `CORE_RESPONSE_TIMEOUT` | `2s` |

La selección es independiente del perfil: `dev` habilita datos/H2, mientras `CORE_MODE` selecciona el transporte. Por defecto todos los perfiles usan `fake`, que registra una simulación sin enviar HTTP. Con `http`, `CoreHttpAdapter` usa `RestClient` de Spring Framework 7.0.10, disponible por las dependencias existentes de Boot 4.1.2, y el cliente JDK de Java 21. No fue necesario agregar otro starter HTTP ni bibliotecas de servidor de pruebas.

La URL y los timeouts se validan al arrancar: URL absoluta HTTP(S), sin credenciales, query ni fragmento, y duraciones positivas. Un modo desconocido impide el arranque. Los timeouts aceptan unidades como `500ms` o `2s`.

El cliente envía un POST JSON a `/core-mock/evento`, relativo a la URL base, y el header `x-api-key` desde la configuración de seguridad existente. No tiene URLs ni puertos codificados en el adaptador. No sigue redirecciones ni implementa reintentos automáticos. El endpoint y la confirmación corresponden al contrato del mock de esta prueba, no a un contrato real del CORE legado.

Solo considera éxito una respuesta 2xx con `success=true`, mensaje no vacío y `body` que confirme `ACTUALIZACION` y el mismo `polizaId`. Respuestas 4xx/5xx, redirecciones, timeout, conexión fallida, cuerpo vacío, JSON inválido o confirmación inesperada se traducen a `CoreNotificationException`; la API responde 502 y revierte el estado local. No se propagan cuerpos externos, URLs ni headers a los errores públicos.

### Ejemplo completo contra el mock local

En una terminal PowerShell:

```powershell
$env:CORE_MODE = 'http'
$env:CORE_BASE_URL = 'http://localhost:8080'
$env:CORE_CONNECT_TIMEOUT = '1s'
$env:CORE_RESPONSE_TIMEOUT = '2s'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

En otra terminal:

```powershell
$coreHeaders = @{ 'x-api-key' = '123456' }
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/polizas/1/renovar' -Headers $coreHeaders
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/polizas/2/cancelar' -Headers $coreHeaders
```

La renovación inicial devuelve 200, estado `RENOVADA`, canon 1050 y prima 12600 con el IPC predeterminado del 5 %. El servicio guarda y hace `flush`, el cliente llama por HTTP al mock protegido en la misma aplicación, el mock registra `Intento CORE evento=ACTUALIZACION polizaId=1`, y confirma el evento antes del commit local. El mock no consulta la base de datos ni vuelve a invocar el servicio, por lo que no genera un ciclo de notificaciones.

Para volver al fake, configurar `CORE_MODE=fake` y reiniciar. Para un mock alojado en otra instancia, ajustar `CORE_BASE_URL`; ambas instancias deben usar la misma clave configurada. Si se usa `server.port=0`, es necesario indicar una URL CORE resoluble explícita: el puerto asignado dinámicamente todavía no existe al crear el cliente.

## Swagger/OpenAPI

Dependencia añadida: `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0`. La [matriz oficial de compatibilidad](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot) incluye Boot 4.1.x con springdoc 3.1.x–3.2.x. No se añadieron dependencias de pruebas.

- UI: **http://localhost:8080/swagger-ui.html** (redirige a `/swagger-ui/index.html`).
- OpenAPI JSON: **http://localhost:8080/v3/api-docs**.
- Usar **Authorize → ApiKey**, ingresar `123456` en desarrollo y ejecutar **Try it out**. La clave se envía como `x-api-key` y no se persiste en la configuración de Swagger UI.
- Se documentan los siete endpoints, filtros, límites de página, requests/responses JSON y errores 400, 401, 404, 409, 500 y 502 cuando corresponden. Los schemas exitosos se generan desde los records genéricos existentes; las anotaciones OpenAPI se limitan a infraestructura.
- No hay JWT, OAuth ni otro mecanismo de autenticación. H2 conserva su excepción de acceso exclusivamente en `dev`.

## Respuestas y errores

`ApiResponse<T>` es un record con métodos estáticos `success(body)`, `success(message, body)` y `error(message)`:

```json
{"success":true,"message":"Operacion realizada correctamente","body":{}}
```

Los errores conservan el mismo contrato:

```json
{"success":false,"message":"API key ausente o incorrecta","body":null}
```

`ResponseEntity` determina el código HTTP en controladores y manejadores. El manejador global cubre validación HTTP/dominio, JSON mal formado, parámetros o enumeraciones inválidos (**400**), recursos inexistentes (**404**), conflictos de negocio, concurrencia e integridad de datos (**409**), fallo de notificación CORE (**502**) y errores inesperados (**500**). Preserva además códigos MVC como **405** y **415**. Los mensajes públicos no contienen trazas ni detalles internos. Los errores de seguridad se escriben desde la cadena de filtros, ya que ocurren antes del controlador; usan el mismo record (401 y 403).

## Endpoints HTTP

Todos requieren `x-api-key` con el valor configurado (`123456` en desarrollo). Los controladores solo validan la entrada, invocan el servicio y construyen respuestas. Los records HTTP omiten la revisión técnica del agregado y no exponen entidades JPA.

| Método y ruta | Entrada | HTTP / `body` exitoso |
| --- | --- | --- |
| `GET /polizas` | `tipo`, `estado`, `page`, `size` opcionales | 200 / `PageResponse<PolizaResponse>` |
| `GET /polizas/{id}/riesgos` | ID positivo de póliza | 200 / lista de riesgos, incluidos cancelados |
| `POST /polizas/{id}/renovar` | ID positivo, sin body | 200 / póliza actualizada |
| `POST /polizas/{id}/cancelar` | ID positivo, sin body | 200 / póliza actualizada |
| `POST /polizas/{id}/riesgos` | JSON `{"id":501}` | 201 / póliza actualizada con sus riesgos |
| `POST /riesgos/{id}/cancelar` | ID positivo de riesgo, sin body | 200 / póliza propietaria actualizada |
| `POST /core-mock/evento` | JSON con `evento=ACTUALIZACION` y `polizaId` positivo | 200 / confirmación del evento recibido |

El alta exige un ID positivo no nulo; todavía no hay generación automática de IDs. Se rechazan fracciones JSON para evitar truncarlas al convertirlas a identificadores. Las reglas de pólizas individuales/colectivas se validan en el dominio. Enumeraciones: `tipo=INDIVIDUAL|COLECTIVA`, `estado=ACTIVA|RENOVADA|CANCELADA`, respetando mayúsculas. Las cancelaciones repetidas devuelven 200 con el mismo estado sin notificación adicional.

Ejemplos en PowerShell, con el proyecto iniciado en `dev`:

```powershell
$apiHeaders = @{ 'x-api-key' = '123456' }
$apiBase = 'http://localhost:8080'
Invoke-RestMethod -Uri "$apiBase/polizas?tipo=COLECTIVA&estado=ACTIVA&page=1&size=20" -Headers $apiHeaders
Invoke-RestMethod -Uri "$apiBase/polizas/1/riesgos" -Headers $apiHeaders
Invoke-RestMethod -Method Post -Uri "$apiBase/polizas/1/renovar" -Headers $apiHeaders
Invoke-RestMethod -Method Post -Uri "$apiBase/polizas/2/riesgos" -Headers $apiHeaders -ContentType 'application/json' -Body '{"id":501}'
Invoke-RestMethod -Method Post -Uri "$apiBase/riesgos/501/cancelar" -Headers $apiHeaders
Invoke-RestMethod -Method Post -Uri "$apiBase/polizas/2/cancelar" -Headers $apiHeaders
```

Respuesta inicial de `GET /polizas/1/riesgos`:

```json
{"success":true,"message":"Operacion realizada correctamente","body":[{"id":101,"estado":"ACTIVO"}]}
```

Respuesta inicial de `GET /polizas?tipo=COLECTIVA&estado=RENOVADA`:

```json
{"success":true,"message":"Operacion realizada correctamente","body":{"data":[],"currentPage":1,"lastPage":0,"totalRecords":0,"hasMorePages":false}}
```

Un fallo CORE retorna 502 con `{"success":false,"message":"No fue posible notificar al CORE","body":null}` y revierte la operación local.

El mock registra mediante SLF4J el evento y el identificador, sin registrar la clave. Acepta identificadores positivos aunque no correspondan a una póliza local: su responsabilidad es registrar el intento. Ejemplo directo:

```powershell
Invoke-RestMethod -Method Post -Uri "$apiBase/core-mock/evento" -Headers $apiHeaders -ContentType 'application/json' -Body '{"evento":"ACTUALIZACION","polizaId":555}'
```

```json
{"success":true,"message":"Operacion realizada correctamente","body":{"evento":"ACTUALIZACION","polizaId":555}}
```

## Datos de desarrollo

`DevelopmentDataInitializer` se activa exclusivamente con el perfil `dev` y `app.dev-data.enabled=true` (habilitado en `application-dev.properties`). Puede deshabilitarse con `APP_DEV_DATA_ENABLED=false`.

| Póliza | Tipo / estado | Riesgos |
| --- | --- | --- |
| 1 | Individual activa | 101 activo |
| 2 | Colectiva activa | 201, 202 y 203 activos |
| 3 | Individual renovada | 301 activo |
| 4 | Colectiva cancelada | 401 y 402 cancelados |

Los datos son ficticios, con inicio original `2026-01-01`, duración de 12 meses y cánones originales 1000, 2000, 1500 y 3000. La póliza 3 representa una renovación histórica al 5 %: canon 1575 y vigencia `[2027-01-01, 2028-01-01)`. Las renovaciones solicitadas por API usan el IPC configurado, no ese dato histórico fijo.

El inicializador guarda solo pólizas ausentes, en una transacción, sin emitir eventos CORE. No duplica ni reinicia estados/importes de pólizas ya existentes. H2 en memoria pierde sus datos al detener el proceso; un nuevo proceso vuelve a cargar los ejemplos. Fuera de `dev` el inicializador no se registra. Las pruebas usan sus propios fixtures; la prueba que activa `dev` deshabilita explícitamente la carga automática y las pruebas del inicializador lo invocan de forma controlada.

## Paginación

`GET /polizas` usa el `PageResponse<T>` existente dentro de `body`:

```json
{"data":[],"currentPage":1,"lastPage":0,"totalRecords":0,"hasMorePages":false}
```

- Página inicial **1**, tamaño predeterminado **20**, máximo **100** (constantes del record).
- Conjunto vacío: `lastPage=0`, `totalRecords=0` y `hasMorePages=false`.
- Se validan `page >= 1` y `1 <= size <= 100`; valores inválidos producen 400. El servicio convierte la página a índice 0 para persistencia y el orden es ascendente por `id`.
- Una página posterior a la última devuelve 200 con `data=[]`, conservando el total y la última página reales. Si no hay resultados, `lastPage=0` incluso si se solicita una página posterior.
- La persistencia ejecuta un conteo, pagina IDs en base de datos y carga los agregados de esos IDs mediante `JOIN FETCH`: tres consultas para una página con datos, sin N+1 ni paginación en memoria de una colección de riesgos. La consulta no paginada previa se conserva para usos internos.

## Pruebas y alcance pendiente

Las pruebas cubren factories y serialización JSON, página vacía, validaciones HTTP, errores 400/404/405/409/500/502, API key ausente/incorrecta/válida, ausencia de sesión y POST sin token CSRF. Los handlers auxiliares viven solo en `src/test`, se importan explícitamente y se excluyen del escaneo normal. Las pruebas Swagger usan la UI y documentación generadas reales, no handlers simulados.

También se prueba el perfil `dev` con servidor embebido y puerto aleatorio: acceso real a H2 sin API key y autenticación HTTP, deshabilitando la carga de ejemplos. El contexto sin perfil comprueba los tres controladores de la aplicación, sin H2 ni inicializador. Las pruebas MockMvc de los seis endpoints de negocio usan H2 y transacciones reales del servicio, verificando persistencia, límites de paginación, consultas sin efectos secundarios, cancelaciones idempotentes y rollback ante errores CORE. La respuesta 409 de concurrencia también tiene una prueba HTTP.

Los tests de `CoreHttpAdapter` usan `HttpServer` del JDK en loopback con puerto efímero, sin servicios externos. Verifican payload, API key, éxito, estados 4xx/5xx, redirección, respuesta inválida, timeout de respuesta y conexión rechazada, sin reintentos. Las pruebas completas levantan Spring Boot con un puerto local disponible y modo `http`: una renovación o cancelación por HTTP llega al mock protegido, genera el log y persiste el resultado. Los tests de configuración verifican que solo exista un adaptador activo y rechazan configuraciones inválidas.

Verificación del sexto incremento: ` .\mvnw.cmd test` ejecutó **233 pruebas, 0 fallos, 0 errores, 0 omitidas**. Se comprobó además una instancia temporal `dev` en modo HTTP: HTML de Swagger UI y OpenAPI disponibles, renovación y cancelación confirmadas por el mock. La instancia de verificación se detuvo al terminar.

H2 y JPA ya existían; se conserva `ddl-auto=update`. Se movieron consola y SQL visible al perfil `dev`. Las advertencias de instrumentación dinámica de Mockito bajo Java 21 provienen de las dependencias existentes de pruebas.

Se conserva la limitación de consistencia distribuida documentada y el uso de H2 en memoria. La integración implementada apunta al contrato mock de la prueba, no al CORE legado real. Distinguir el diseño teórico del módulo 1 de la implementación mínima del módulo 2. No se agregaron JWT, Lombok, MapStruct, reintentos, Outbox ni mensajería.
