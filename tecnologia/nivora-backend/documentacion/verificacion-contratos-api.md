# Contratos y aislamiento de la API

La segunda parte del bloque 9 comprueba los contratos HTTP de consulta
y administración mediante sesiones reales de prueba, controles de permisos,
fuentes HTTP controladas y repositorios JDBC.

Las pruebas usan el mismo reloj y datos con resultados distintos para los
suministros ficticios `000101` y `000202`. Se crean dos sesiones independientes
del primer suministro y una del segundo. Las API de agua e identidad se
ponen en estado de caída después del inicio de las sesiones.

## Consultas por suministro

Las ocho rutas están bajo `/api/v1/acceso/consulta`.

| Ruta GET | Dato comprobado para 000101 | Dato comprobado para 000202 |
| --- | --- | --- |
| `/consumo-actual` | Acumulado de 2200 L. | Acumulado de 9000 L. |
| `/historial?meses=1` | Octubre con 2000 L. | Octubre con 10000 L. |
| `/estadisticas?meses=1` | Acumulado actual de 2200 L. | Acumulado actual de 9000 L. |
| `/costos?meses=1` | Consumo usado para el costo de 2200 L. | Consumo usado para el costo de 9000 L. |
| `/comparacion` | Acumulado comparado de 2200 L. | Acumulado comparado de 9000 L. |
| `/proyeccion` | Consumo observado de 2200 L. | Consumo observado de 9000 L. |
| `/recibos` | Recibo `REC-101-10`. | Recibo `REC-202-10`. |
| `/notificaciones` | Aviso del recibo `REC-101-10`. | Aviso del recibo `REC-202-10`. |

Cada respuesta válida devuelve HTTP 200, contenido JSON y
`Cache-Control: no-store`. Los campos de volumen y número de recibo se
comprueban como cadenas. Las dos sesiones del mismo suministro reciben
respuestas equivalentes. Añadir `codigoSuministro` con el código ajeno a
la URL conserva el suministro de la sesión.

Una solicitud sin token, con un token administrativo en estas rutas o con
una sesión cerrada devuelve HTTP 401. El cierre de una sesión conserva
el acceso de la otra. Los contadores de las fuentes comprueban que
las consultas locales no vuelven a validar agua o identidad.

## Operaciones administrativas

La matriz cubre trece operaciones.

| Área | Operaciones comprobadas |
| --- | --- |
| Suministros y accesos | GET del listado y GET del historial de accesos. |
| Tarifas | GET de gestión y PUT de una publicación válida. |
| Alertas de consumo | GET de configuración y PUT de una pausa válida. |
| Periodos mensuales | GET del calendario y PUT de una regla futura válida. |
| Supervisión | GET de lecturas, GET de recibos y GET de incidencias. |
| Reintentos | POST de lecturas y POST de recibos. |

Cada operación devuelve HTTP 401 sin sesión o con una sesión de consulta.
Una cuenta administrativa con cambio de clave requerido recibe HTTP 403.
Se comprueba que esos rechazos conservan los registros de configuración y
los calendarios de negocio. Al restituir el permiso, las solicitudes válidas
devuelven HTTP 200 o HTTP 202 según su contrato.

Los errores verificados usan `application/problem+json` y los campos
`status`, `title` y `detail`. Los errores HTTP 401 incluyen
`WWW-Authenticate: Bearer`. En los casos comprobados, las respuestas
omiten el DNI, la clave administrativa y los tokens enviados.

## Validaciones y escrituras entre suministros

Seis publicaciones inválidas cubren tipos incorrectos de revisión y día,
revisión nula, campos adicionales, claves duplicadas y contenido después
del objeto JSON. Todas devuelven HTTP 400 y conservan la revisión inicial.

Otro caso configura un dispositivo por la API y comprueba que la sesión
del suministro ajeno recibe HTTP 404 al intentar marcar su aviso. El
intento de asociar el mismo endpoint Web Push devuelve HTTP 409. La baja
solicitada desde el suministro ajeno conserva la suscripción original y
los avisos de su bandeja.

## Repetir en Windows

Desde CMD en `tecnologia\nivora-backend`, detén el proceso del JAR con
Ctrl+C y ejecuta la suite completa.

```cmd
mvnw.cmd clean verify
```

Para repetir la matriz actual, usa esta línea.

```cmd
mvnw.cmd "-Dtest=AccesoConsultaIntegradoTests#contrato*+laSesionAjenaNoPuedeMarcarAvisosNiRetirarLaSuscripcionDelOtroSuministro" test
```

La suite usa H2 y fuentes controladas. Los mismos 27 casos también se
ejecutaron con MySQL 8.0.46 en una base aislada. La equivalencia se comprueba
en las respuestas HTTP de sesiones independientes.

## Ampliación de RF20

La nueva comparación conserva el suministro de la sesión y la equivalencia
entre dispositivos, aun con las fuentes externas caídas. La matriz de
consulta tiene ahora ocho operaciones. Los 27 casos del corte original
se conservan como registro histórico; la ejecución actual de ese comando
incluye 28. La [evidencia de comparación](evidencias/comparacion-costos-2026-11-19.json)
registra la regresión y los casos MySQL de esta ampliación.
