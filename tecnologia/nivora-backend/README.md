# Backend de Nivora

Implementacion del backend propio con Java 21, Spring Boot 4.1.2 y MySQL.
Se desarrolla como monolito modular, con dependencias organizadas segun
Clean Architecture.

## Servicios del entorno local

| Componente | Puerto | Base de datos |
|---|---|---|
| Empresa de Agua | 8081 | `empresa_agua` |
| Backend de Nivora | 8082 | `nivora` |

El puerto 8080 esta ocupado por Jenkins en el equipo de desarrollo.

## Modulos de negocio

| Paquete previsto | Modulo | Estado |
|---|---|---|
| `acceso` | Acceso y suministros | Modelos, contrato y adaptador HTTP de suministros. |
| `lecturas` | Lecturas, consumo e historial | Pendiente de implementar. |
| `tarifas` | Tarifas, costos y proyecciones | Pendiente de implementar. |
| `notificaciones` | Alertas y notificaciones | Pendiente de implementar. |
| `recibos` | Recibos | Pendiente de implementar. |

Las capas de cada modulo se incorporaran cuando se implementen sus
responsabilidades: dominio, aplicacion, infraestructura y presentacion.
El dominio no depende de Spring, JDBC ni clientes HTTP.

## Primer contrato: consulta de suministros

Los archivos se encuentran en
`src/main/java/pe/nivora/backend/acceso/dominio/`.

| Tipo | Responsabilidad |
|---|---|
| `CodigoSuministro` | Validar entre 1 y 20 digitos ASCII y conservar los ceros iniciales. |
| `Suministro` | Representar el codigo y el titular asociado al suministro. |
| `FuenteSuministros` | Definir la consulta externa necesaria durante la validacion de un nuevo acceso. |
| `FuenteSuministrosNoDisponibleException` | Distinguir una incidencia externa de un suministro inexistente. |

El contrato devuelve un suministro cuando la fuente lo encuentra y un
`Optional` vacio cuando la fuente confirma que no existe. Un fallo de
comunicacion o una respuesta no utilizable debe producir la excepcion
de fuente no disponible; no debe convertirse en un resultado inexistente.

Este contrato corresponde a la comprobacion del suministro durante
un nuevo acceso. La validacion del DNI, la sesion y el registro del acceso
se implementaran en sus siguientes casos de uso. El titular del suministro
y la persona que consulta pueden ser diferentes.

El adaptador HTTP implementa este contrato en infraestructura.
Las consultas del panel utilizaran datos aceptados en el almacenamiento
propio de Nivora; no dependeran de una llamada a la Empresa por cada
solicitud del usuario.

### Adaptador de la Empresa de Agua

La API Java de la Empresa de Agua ofrece la consulta independiente
`GET /api/v1/suministros/{codigo}`, con `codigoSuministro` y `titular`,
ademas de las consultas de consumo actual, historial mensual, tarifa
vigente y recibos. `FuenteSuministrosHttp`, ubicado en
`acceso/infraestructura/empresaagua/`, implementa `FuenteSuministros`
mediante `RestClient`. Spring registra una instancia mediante
`EmpresaAguaConfiguracion`.

| Respuesta externa | Resultado del adaptador |
|---|---|
| HTTP 200, JSON con el mismo codigo y titular no vacio | Suministro encontrado. |
| HTTP 404, ProblemDetail del suministro solicitado con titulo `Suministro no encontrado` | Ausencia confirmada: `Optional.empty()`. |
| Otros estados, redirecciones, JSON invalido o datos incompatibles | Fuente no disponible. |
| Conexion rechazada o tiempo de espera agotado | Fuente no disponible. |

Los codigos se conservan como texto. No se siguen redirecciones y el
cuerpo de respuesta se limita a 64 KiB. Los mensajes de error propios
no incluyen el cuerpo recibido. Un 404 generico de una ruta incorrecta
no confirma que el suministro sea inexistente.

La configuracion esta en `application.properties`:

| Propiedad | Valor local | Significado |
|---|---|---|
| `nivora.empresa-agua.url-base` | `http://127.0.0.1:8081` | Origen HTTP del proveedor, sin ruta, credenciales ni parametros. |
| `nivora.empresa-agua.conexion-ms` | `3000` | Espera maxima para establecer la conexion. |
| `nivora.empresa-agua.lectura-ms` | `5000` | Espera maxima sin recibir datos durante una lectura de la respuesta. |
| `nivora.empresa-agua.comprobar-al-iniciar` | `false` | Activa solo la comprobacion manual por consola. |
| `nivora.empresa-agua.codigo-prueba` | `13100390` | Codigo utilizado por esa comprobacion. |

Estos tiempos son limites de red, no frecuencias de sincronizacion.
El arranque habitual no consulta a la Empresa de Agua.

### Integracion pendiente

Este avance incorpora la consulta externa de suministros. Aun faltan
el caso de uso de acceso, la validacion de identidad, sesiones, roles y
la persistencia y sincronizacion de datos propios. La comprobacion de
consola no publica un endpoint de consulta en Nivora.

Para la sincronizacion de lecturas tambien debe definirse su entrega
con identificacion, fechas, unidad, medidor y recuperacion de pendientes,
segun los requisitos y ADR-003. Un acumulado mensual no equivale a
un incremento nuevo que deba sumarse cada vez que se consulta.

## Configuracion y comprobaciones

La ejecucion normal utiliza `application.properties` y obtiene la
contrasena de `nivora_app` mediante la variable `NIVORA_DB_CLAVE`.
La contrasena no debe guardarse en los archivos del proyecto.

Desde `tecnologia/nivora-backend` en CMD:

```bat
mvnw.cmd clean verify
```

Las pruebas de arranque usan el perfil `test`, H2 en memoria y un
puerto HTTP temporal. Comprueban que la conexion de pruebas utiliza H2
y que `/actuator/health` responde con HTTP 200.

Las pruebas del adaptador levantan un servidor HTTP temporal en
`127.0.0.1` y comprueban el transporte real, respuestas validas, ausencia
confirmada, datos invalidos, errores HTTP, redirecciones, conexion
rechazada y tiempos de espera tanto en cabeceras como en el cuerpo.
No necesitan que MySQL ni la Empresa de Agua esten ejecutandose.

Estas pruebas no comprueban la conexion a la Empresa de Agua instalada
en el equipo, las futuras reglas de negocio ni la capacidad de 5000 usuarios.

Con Nivora ejecutandose normalmente:

```bat
curl.exe -i "http://127.0.0.1:8082/actuator/health"
```

### Comprobacion manual con la Empresa de Agua local

Mantener la Empresa de Agua iniciada en el puerto 8081. Desde el backend,
con `NIVORA_DB_CLAVE` disponible en el entorno, ejecutar en CMD:

```bat
mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.main.web-application-type=none --nivora.empresa-agua.comprobar-al-iniciar=true --nivora.empresa-agua.codigo-prueba=13100390"
```

Esta ejecucion no abre el puerto 8082 y solo consulta el suministro.
Debe mostrar `Comprobacion Empresa de Agua: suministro 13100390 encontrado.`
Para comprobar la ausencia confirmada, usar `99999999` como codigo de
prueba. Si la fuente no esta disponible, la ejecucion falla con
`FuenteSuministrosNoDisponibleException`; no informa una ausencia falsa.

## Referencias de arquitectura

- [Modulos del backend](../../docs/decisiones/ADR-001-monolito-modular.md).
- [Clean Architecture](../../docs/decisiones/ADR-002-clean-architecture.md).
- [Actualizacion y continuidad](../../docs/decisiones/ADR-003-actualizacion-y-continuidad.md).
- [Contratos y adaptadores](../../docs/decisiones/ADR-004-integraciones-externas.md).
