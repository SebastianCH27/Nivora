# ADR-003: Separar las consultas de la actualización externa

- **Estado:** Aceptado para el diseño inicial.
- **Fecha:** 2026-10-11.

## Contexto

Nivora obtiene suministros, lecturas, tarifas y recibos
mediante la integración con la empresa de agua.

La fuente puede presentar fallos de comunicación,
datos incompletos o registros repetidos.

Consultar directamente esa API en cada solicitud del usuario
haría que las consultas dependieran de su disponibilidad
y tiempo de respuesta.

Nivora necesita conservar información propia para atender
las consultas, indicar su actualidad y recuperar los datos
pendientes cuando se restablezca la comunicación.

Esta decisión responde a los siguientes drivers:

| Driver | Necesidad |
|--------|-----------|
| DA05 — Continuidad ante fallos externos | Mantener disponibles las consultas almacenadas cuando falle la API de la empresa. |
| DA01 — Capacidad y rendimiento | Atender consultas simultáneas mientras continúan las actualizaciones y la evaluación de alertas. |
| DA02 — Exactitud e integridad | Validar las lecturas y evitar su doble conteo durante actualizaciones y reintentos. |
| DA08 — Recibos de la empresa | Conservar los recibos recibidos y sus fechas, diferenciándolos de las estimaciones de Nivora. |

## Decisión

Separar el proceso de actualización externa
de las consultas realizadas por los usuarios.

Las consultas utilizarán el almacenamiento propio de Nivora.
No solicitarán nuevas lecturas a la empresa
como condición para responder cada consulta.

Un proceso controlado obtendrá los datos externos
según la frecuencia configurada y las condiciones de la fuente.

La incorporación seguirá este procedimiento:

1. Obtener los registros disponibles de la empresa.
2. Validar su estructura, valores y correspondencia.
3. Detectar registros previamente incorporados.
4. Almacenar los nuevos datos aceptados.
5. Actualizar el estado de la integración.
6. Evaluar las condiciones de alerta y disponibilidad de recibos.

Los cálculos utilizarán las lecturas aceptadas
y las versiones tarifarias compatibles con el periodo.

La entrega de avisos al dispositivo se procesará
de forma independiente de las consultas.

## Validación y control de duplicados

Las lecturas conservarán su identificación, fuente,
suministro, medidor, fecha, unidad y tipo de medición.

Una lectura ya incorporada no volverá a contabilizarse
durante una actualización o reintento.

Si un identificador existente llega con valores diferentes,
se registrará una incidencia para revisión,
sin reemplazar silenciosamente la lectura aceptada.

Los registros inválidos se excluirán de los cálculos
y se conservará el motivo de rechazo.

Los avisos utilizarán una identificación del evento
para evitar notificaciones repetidas por la misma causa.

## Comportamiento ante fallos

Cuando falle la API de la empresa:

- Se conservarán las lecturas, tarifas y recibos aceptados.
- Las sesiones de consulta vigentes podrán seguir
  consultando la información almacenada.
- La interfaz indicará el fallo y la fecha de la última lectura.
- No se presentará la información conservada
  como una nueva medición.
- Se registrará una incidencia de comunicación.
- Se reintentará la actualización cuando corresponda.

Al restablecerse la conexión, se recuperarán los registros
disponibles que todavía no hayan sido incorporados,
manteniendo las validaciones y la deduplicación.

Esta continuidad no permite omitir las validaciones del acceso.
Si un nuevo inicio de sesión necesita una API que no responde,
se informará que no fue posible completar la validación.

## Justificación

La separación permite atender consultas sin esperar
una nueva respuesta de la empresa en cada solicitud.

El almacenamiento propio conserva información utilizable
durante una interrupción externa y permite explicar
qué lecturas y tarifas sustentan los resultados.

El control de duplicados hace posible repetir
una actualización sin incrementar artificialmente el consumo.

Conservar los recibos proporcionados por la empresa
permite mostrar sus importes definitivos
sin confundirlos con los costos estimados por Nivora.

## Consecuencias

- Nivora necesitará almacenamiento propio y supervisión
  del proceso de actualización.
- La información podrá tener un retraso respecto de la fuente;
  su fecha y estado deberán mostrarse.
- Las consultas deberán identificar datos incompletos
  o incompatibles, sin sustituirlos por valores inventados.
- Los reintentos deberán conservar las validaciones
  y el control de duplicados.
- Al utilizar varias instancias, deberán coordinarse
  las tareas de incorporación y generación de avisos.
- La frecuencia de actualización dependerá de la fuente
  y deberá evaluarse junto con las pruebas de rendimiento.

## Alternativa considerada

Consultar la API de la empresa en cada solicitud
reduciría la necesidad de conservar parte de los datos localmente.

Sin embargo, trasladaría sus fallos y tiempos de respuesta
a las consultas de Nivora y repetiría solicitudes externas
para información que puede compartirse entre usuarios.

Se adopta la actualización controlada
con consultas sobre información almacenada.

## Relación con el boilerplate

El caso de aplicación `actualizar-datos.js` incorpora
la información obtenida por el adaptador HTTP.

`consultar-panel.js` prepara los resultados
a partir del repositorio propio.

`server.js` organiza las actualizaciones periódicas
y la entrega de notificaciones.

Las pruebas verifican que las consultas continúen
durante una caída de la empresa y que los registros
duplicados no alteren el consumo.

El adaptador JSON representa el almacenamiento local
de la demostración. Su uso en una sola instancia
no acredita durabilidad de producción ni capacidad
para atender 5000 usuarios simultáneamente.

La selección del almacenamiento para un despliegue mayor
deberá justificarse y comprobarse mediante pruebas.