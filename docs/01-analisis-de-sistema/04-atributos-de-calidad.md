# Atributos de calidad de Nivora

Los atributos de calidad describen las condiciones de funcionamiento
que Nivora debe cumplir.

El sistema deberá atender 5000 usuarios simultáneamente mientras
continúan la actualización de lecturas y la evaluación de alertas.
Esta capacidad deberá comprobarse mediante pruebas.

## Atributos identificados

| ID | Atributo de calidad | Descripción |
|----|---------------------|-------------|
| AC01 | Rendimiento | Atender 5000 usuarios activos simultáneamente, manteniendo tiempos de respuesta adecuados para las consultas de consumo, costos, proyección, historial, recibos y notificaciones. |
| AC02 | Escalabilidad | Permitir ampliar la capacidad cuando aumenten los usuarios, las lecturas y los datos históricos. La capacidad alcanzada deberá comprobarse mediante pruebas con cargas crecientes. |
| AC03 | Exactitud e integridad | Calcular correctamente consumos, costos y proyecciones a partir de datos válidos, evitando el doble conteo de lecturas y el uso de información incompatible. |
| AC04 | Continuidad ante fallos externos | Mantener las consultas de información almacenada cuando falle la API de la empresa, mostrando el estado de actualización. Un fallo en el envío de notificaciones debe conservar los avisos dentro de Nivora y no bloquear las consultas. |
| AC05 | Actualidad y trazabilidad | Mostrar la fecha de la última lectura y conservar las referencias a las lecturas, tarifas y periodos utilizados para explicar los resultados. |
| AC06 | Seguridad | Proteger las comunicaciones mediante HTTPS, comprobar las sesiones y restringir las operaciones administrativas según los permisos. Los registros de acceso y datos del DNI deberán estar protegidos frente a consultas no autorizadas. |
| AC07 | Interoperabilidad | Intercambiar información con las API de la empresa de agua, identidad y notificaciones respetando sus formatos y condiciones de comunicación. En esta etapa, la integración con la empresa será simulada. |
| AC08 | Usabilidad | Presentar información legible y comprensible en distintos dispositivos, diferenciando consumo, costo estimado, proyección e importe definitivo del recibo. Los errores de acceso deberán explicar qué ocurrió. |
| AC09 | Consistencia entre dispositivos | Mostrar resultados equivalentes para el mismo suministro, periodo, versión de lecturas y tarifas, independientemente del dispositivo utilizado. |
| AC10 | Mantenibilidad | Mantener responsabilidades separadas para facilitar cambios en tarifas, proyecciones, alertas e integraciones sin introducir fallos en otras funciones. |

## Metas iniciales para la prueba de carga

La capacidad de 5000 usuarios simultáneos es un requisito del proyecto.
Los siguientes valores complementarios se proponen como metas iniciales
para evaluar su funcionamiento:

| Indicador | Meta |
|-----------|------|
| Carga sostenida | Mantener 5000 usuarios activos simultáneamente durante 30 minutos, después de un incremento gradual. |
| Tiempo de respuesta | Al menos el 95 % de las solicitudes válidas de cada operación principal deberá responder en un máximo de 2 segundos. |
| Errores internos | Menos del 1 % de las solicitudes válidas de cada operación deberá fallar por errores internos o tiempos de espera agotados. |
| Corrección de resultados | Cero cálculos incorrectos, dobles conteos o accesos administrativos indebidos en las verificaciones realizadas. |

La prueba representará usuarios con sesiones independientes que realicen
consultas de consumo, costos, proyección, historial, recibos y notificaciones.
Durante la prueba también se incorporarán lecturas y se evaluarán alertas.

Los tiempos se medirán en la API propia de Nivora. Las respuestas esperadas
de rechazo por datos inválidos no se contabilizarán como errores internos.

Se documentarán la infraestructura utilizada, el volumen de datos,
las operaciones ejecutadas, los tiempos de respuesta, los errores
y el consumo de recursos.

## Comprobaciones complementarias

- Interrumpir la API simulada y comprobar que las consultas almacenadas
  continúen disponibles, indicando la antigüedad de la información.
- Incorporar lecturas duplicadas, inválidas o incompletas y comprobar
  que no produzcan resultados incorrectos.
- Verificar que una sesión de consulta no pueda ejecutar funciones
  administrativas ni consultar registros de acceso.
- Comparar resultados desde distintos dispositivos utilizando
  el mismo suministro, periodo y versión de datos.
- Comprobar las notificaciones con Nivora cerrada, en dispositivos
  conectados y con el permiso de notificación habilitado.