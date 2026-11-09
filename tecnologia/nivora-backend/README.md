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
| `acceso` | Acceso y suministros | Primeros modelos y contrato de consulta. |
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

El adaptador HTTP implementara este contrato en infraestructura.
Las consultas del panel utilizaran datos aceptados en el almacenamiento
propio de Nivora; no dependeran de una llamada a la Empresa por cada
solicitud del usuario.

### Integracion pendiente

La API Java existente de la Empresa de Agua ofrece consultas por codigo
de consumo actual, historial mensual, tarifa vigente y recibos.
Todavia debemos incorporar una consulta independiente del suministro
antes de conectar este primer contrato.

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

Las dos pruebas de arranque usan el perfil `test`, H2 en memoria y un
puerto HTTP temporal. Comprueban que la conexion de pruebas utiliza H2
y que `/actuator/health` responde con HTTP 200.

Estas pruebas no comprueban la integracion con la Empresa de Agua,
las futuras reglas de negocio ni la capacidad de 5000 usuarios.

Con Nivora ejecutandose normalmente:

```bat
curl.exe -i "http://127.0.0.1:8082/actuator/health"
```

## Referencias de arquitectura

- [Modulos del backend](../../docs/decisiones/ADR-001-monolito-modular.md).
- [Clean Architecture](../../docs/decisiones/ADR-002-clean-architecture.md).
- [Actualizacion y continuidad](../../docs/decisiones/ADR-003-actualizacion-y-continuidad.md).
- [Contratos y adaptadores](../../docs/decisiones/ADR-004-integraciones-externas.md).
