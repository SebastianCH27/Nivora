# Patrones de diseño de Nivora

## Objetivo

Documentar los patrones utilizados en el backend de Nivora, indicando
dónde se aplican, qué problema resuelven y cuáles son sus archivos
de referencia. La descripción se basa en el código revisado en el
commit `89e5c69`.

## Contexto

- **Estilo arquitectónico:** monolito modular con Node.js, JavaScript y Express.
- **Enfoque interno:** Clean Architecture con contratos del núcleo y adaptadores de infraestructura.
- **Organización actual:** carpetas compartidas por capas, con los cinco módulos de negocio definidos en la arquitectura.
- **Integraciones de la demostración:** API simuladas de empresa de agua e identidad; adaptador Web Push que requiere configuración y verificación de recepción.

Los medidores y los sistemas internos de la empresa pertenecen a la
infraestructura externa prevista. La integración real con RENIEC y la
posibilidad de pagar mediante Yape corresponden a etapas posteriores.

## Patrones aplicados

Las rutas de la tabla son relativas a
`tecnologia/nivora-boilerplate/src/`.

| Patrón | Dónde se usa | Problema que resuelve | Implementación actual | Archivos de referencia |
|--------|-------------|----------------------|-----------------------|------------------------|
| Adapter | Acceso y actualización → empresa de agua | Separar las operaciones que necesita Nivora de las rutas, credenciales, respuestas y errores de la API externa. | `FuenteAguaHttp` implementa el contrato `FuenteAgua` mediante HTTP y `fetch`. | `dominio/contratos/puertos.js`, `infraestructura/fuentes-http.js` |
| Adapter | Acceso → servicio de identidad | Consultar la existencia del DNI y sus datos sin incorporar detalles de la fuente en el caso de aplicación. | `FuenteIdentidadHttp` implementa `FuenteIdentidad` contra la API simulada con personas ficticias. | `dominio/contratos/puertos.js`, `infraestructura/fuentes-http.js` |
| Adapter | Entrega de avisos → servicio de notificaciones | Mantener los detalles de Web Push y su biblioteca separados de la coordinación de avisos. | `NotificadorWebPush` implementa `EmisorNotificaciones` utilizando la biblioteca `web-push`. | `dominio/contratos/puertos.js`, `infraestructura/notificador-web-push.js`, `aplicacion/notificaciones.js` |
| Repository, mediante una abstracción simplificada de estado | Casos de aplicación → persistencia propia | Concentrar el acceso al almacenamiento y permitir utilizar implementaciones diferentes en la demostración y las pruebas. | Contrato `RepositorioNivora`, con `RepositorioMemoria` y `RepositorioArchivo`; este último extiende al primero y conserva el estado en JSON. | `dominio/contratos/puertos.js`, `infraestructura/repositorio-memoria.js`, `infraestructura/repositorio-archivo.js` |

## Cómo funciona cada patrón

### Adapter

Los casos de aplicación utilizan operaciones expresadas en términos
de Nivora: `consultarSuministro(codigo)`, `obtenerDatos()`,
`consultarDni(dni)` y `enviar(dispositivo, aviso)`.

Los adaptadores convierten esas operaciones en las llamadas concretas
de cada servicio. Por ejemplo, `FuenteAguaHttp.obtenerDatos()` consulta
la ruta `/agua/datos` de la fuente simulada. Los detalles HTTP permanecen
en infraestructura; las lecturas recibidas se validan mediante las reglas
del núcleo antes de incorporarse.

`NotificadorWebPush` construye el mensaje y utiliza la suscripción del
dispositivo para solicitar el envío. `GestionarNotificaciones` coordina
los pendientes, los resultados y los reintentos. La aceptación por el
servicio externo se registra como tal; comprobar la recepción en pantalla
requiere una verificación en el dispositivo.

La sustitución de una fuente simulada por una real requiere implementar
y comprobar su adaptador, respetando las operaciones y los datos
acordados en el contrato.

### Repository: abstracción de persistencia del prototipo

Los casos de aplicación utilizan `leer()` y `modificar(operacion)`
del repositorio recibido por el constructor.

`RepositorioMemoria` conserva el estado y ejecuta callbacks de modificación
sincrónicos. `RepositorioArchivo` reutiliza ese comportamiento y organiza
su escritura diferida en un archivo JSON.

Esta variante concentra el mecanismo de almacenamiento, aunque los casos
de aplicación conocen la estructura del estado devuelto. Un contrato con
operaciones específicas por responsabilidad permitiría reducir ese
acoplamiento si la evolución del sistema lo requiere.

El adaptador de archivo corresponde a una demostración de una sola
instancia. La elección de persistencia para un despliegue mayor deberá
justificarse y comprobarse con las metas de calidad del proyecto.

## Técnica complementaria: inyección de dependencias

La inyección de dependencias permite entregar los colaboradores que
necesita cada caso de aplicación. Se utiliza junto con los patrones
anteriores y no se clasifica aquí como un patrón GoF.

La raíz de composición selecciona las implementaciones en `src/app.js`.
Por ejemplo, estos fragmentos conectan la fuente, el repositorio
y los casos de consulta y actualización:

```js
const agua = new FuenteAguaHttp(baseSimulacion, secreto);
const panel = new ConsultarPanel({ repositorio, reloj });
const actualizacion = new ActualizarDatos({ agua, repositorio, panel, reloj, identificador });
```

El repositorio se abre en `src/server.js` y se entrega a `componer()`.
También se inyectan el reloj y el generador de identificadores, lo que
permite controlar esos valores durante las pruebas.

La aplicación utiliza los objetos recibidos y sus contratos. La selección
de los adaptadores concretos permanece en la composición del sistema.

## Beneficios para Nivora

- Mantener los detalles de las API y Web Push separados de las reglas de consumo, costos y alertas.
- Sustituir implementaciones mediante contratos y pruebas de compatibilidad.
- Utilizar repositorios y emisores de prueba para estudiar fallos, duplicados y reintentos.
- Conservar la actualización externa separada de las consultas sobre datos propios.

El último beneficio depende también de la coordinación definida en
ADR-003: los patrones por sí solos no establecen la frecuencia de
actualización ni la capacidad de usuarios simultáneos.

## Posible evolución

| Patrón | Dónde podría aplicarse | Condición para evaluarlo | Estado actual |
|--------|------------------------|--------------------------|---------------|
| Observer | Colaboración entre incorporación de lecturas o recibos y generación de avisos. | Cuando varios módulos necesiten reaccionar al mismo evento y resulte conveniente separar sus suscripciones. Deberá conservarse la identificación de eventos, el control de duplicados y la recuperación ante fallos. | Propuesta de evolución. Actualmente `ActualizarDatos` registra los avisos y `GestionarNotificaciones` procesa sus entregas mediante tareas programadas. |

Esta propuesta se evaluará según las necesidades del sistema.
El funcionamiento actual de las notificaciones se describe en el
documento de diseño interno.

## Referencias del proyecto

- [Diseño interno de módulos](diseño-interno-de-modulos.md)
- [Componentes arquitectónicos](../../02-arquitectura-software/componentes-arquitectonicos.md)
- [Contratos del núcleo](../../../tecnologia/nivora-boilerplate/src/dominio/contratos/puertos.js)
- [Adaptadores HTTP](../../../tecnologia/nivora-boilerplate/src/infraestructura/fuentes-http.js)
- [Adaptador Web Push](../../../tecnologia/nivora-boilerplate/src/infraestructura/notificador-web-push.js)
- [Repositorio en memoria](../../../tecnologia/nivora-boilerplate/src/infraestructura/repositorio-memoria.js)
- [Repositorio de archivo](../../../tecnologia/nivora-boilerplate/src/infraestructura/repositorio-archivo.js)
- [Raíz de composición](../../../tecnologia/nivora-boilerplate/src/app.js)
- [ADR-002: Clean Architecture](../../decisiones/ADR-002-clean-architecture.md)
- [ADR-003: Actualización y continuidad](../../decisiones/ADR-003-actualizacion-y-continuidad.md)
- [ADR-004: Integraciones externas](../../decisiones/ADR-004-integraciones-externas.md)