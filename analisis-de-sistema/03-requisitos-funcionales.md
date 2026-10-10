# Requisitos funcionales de Nivora

Los requisitos funcionales describen las funciones que Nivora debe
proporcionar para atender las necesidades de sus usuarios.

Durante esta primera etapa, la información de suministros, lecturas,
tarifas y recibos se obtendrá mediante una API simulada que represente
al sistema de la empresa de agua.

## Requisitos funcionales

| ID | Requisito funcional |
|----|---------------------|
| RF01 | El sistema debe permitir ingresar el código de suministro del hogar y el DNI de la persona que consulta, comprobando que ambos campos estén completos y tengan el formato requerido. |
| RF02 | El sistema debe verificar que el código corresponda a un suministro existente y validar el DNI mediante una API de identidad, obteniendo los datos asociados al documento. Si los datos son incorrectos, debe mostrar un error; si no puede completar la validación, debe informar esa situación. |
| RF03 | El sistema debe iniciar una sesión únicamente después de completar las validaciones, asociarla al suministro consultado, comprobarla en las operaciones protegidas y permitir cerrarla. |
| RF04 | El sistema debe registrar cada acceso exitoso con el suministro consultado, el DNI utilizado, los datos obtenidos de la API de identidad y la fecha y hora del acceso. |
| RF05 | El sistema debe obtener automáticamente las lecturas disponibles mediante la API simulada de la empresa de agua, según la frecuencia de actualización definida para la demostración. |
| RF06 | El sistema debe registrar cada lectura con su suministro, identificación del medidor, fecha de medición, valor, unidad, tipo de medición, fuente y fecha de recepción. |
| RF07 | El sistema debe validar los campos, unidades, valores y correspondencia del suministro antes de incorporar una lectura a los cálculos, registrando el motivo de rechazo cuando sea inválida. |
| RF08 | El sistema debe detectar lecturas ya recibidas y evitar que una misma lectura se contabilice más de una vez. |
| RF09 | El sistema debe registrar los fallos de comunicación y recuperar las lecturas pendientes cuando se restablezca la conexión, evitando duplicados. |
| RF10 | El sistema debe calcular el consumo mediante diferencias entre lecturas acumuladas compatibles o mediante la suma de consumos de intervalos no superpuestos, según el tipo de dato recibido. |
| RF11 | El sistema debe mostrar el consumo en litros y permitir su consulta equivalente en metros cúbicos. |
| RF12 | El sistema debe mostrar el consumo acumulado del mes hasta la última lectura disponible, indicando las fechas que delimitan el cálculo. |
| RF13 | El sistema debe identificar información faltante o lecturas incompatibles e indicar cuándo el consumo está incompleto o no puede calcularse. |
| RF14 | El sistema debe mostrar la fecha y hora de la última lectura disponible y el estado de actualización del suministro. |
| RF15 | El sistema debe calcular el costo acumulado estimado utilizando el consumo disponible y las tarifas correspondientes al suministro y al periodo. |
| RF16 | El sistema debe mostrar el importe estimado, el consumo utilizado, la tarifa aplicada y los conceptos incluidos en el cálculo. |
| RF17 | El sistema debe proyectar el consumo y su costo al finalizar el mes, considerando que se mantiene el ritmo promedio de consumo registrado durante el periodo transcurrido. |
| RF18 | El sistema debe indicar la fecha de cálculo y los supuestos de la proyección, e informar cuando los datos sean insuficientes para generarla. |
| RF19 | El sistema debe permitir consultar el consumo y los costos estimados de meses anteriores, indicando la cobertura de los datos utilizados. |
| RF20 | El sistema debe permitir comparar periodos, mostrando sus fechas, duración y diferencias de consumo y costo estimado. |
| RF21 | El sistema debe mostrar gráficos y estadísticas de la evolución del consumo, identificando los intervalos sin información. |
| RF22 | El sistema debe generar una alerta cuando el consumo acumulado del mes actual supere el consumo total del mes anterior, siempre que existan datos suficientes para realizar la comparación. |
| RF23 | El sistema debe permitir asociar un dispositivo al suministro consultado y activar o desactivar la recepción de notificaciones, considerando el permiso de notificación del dispositivo. |
| RF24 | El sistema debe registrar las alertas y notificaciones con su suministro, fecha y motivo, evitando avisos duplicados del mismo evento. |
| RF25 | El sistema debe permitir consultar las notificaciones dentro de Nivora, distinguir las pendientes y marcarlas como leídas. |
| RF26 | El sistema debe enviar las notificaciones de consumo y recibos a los dispositivos asociados al suministro, incluso cuando la aplicación esté cerrada. |
| RF27 | El sistema debe obtener mediante la API simulada los recibos mensuales que representen los emitidos por la empresa, identificando el suministro, mes, importe definitivo y fecha de disponibilidad. |
| RF28 | El sistema debe permitir consultar el recibo del mes y los recibos anteriores disponibles, mostrando el importe definitivo y su fecha límite de pago. |
| RF29 | El sistema debe notificar cuando el recibo mensual esté disponible, indicando el mes correspondiente y la fecha límite de pago. Para la demostración, esta fecha se calculará considerando una semana desde la disponibilidad del recibo. |
| RF30 | El sistema debe permitir el acceso administrativo mediante un mecanismo diferenciado del acceso de consulta, comprobando los permisos necesarios para cada operación administrativa. |
| RF31 | El sistema debe permitir al administrador consultar los suministros reconocidos por la integración y los registros de acceso a la plataforma. |
| RF32 | El sistema debe permitir configurar los periodos mensuales utilizados para organizar las consultas, cálculos, comparaciones y recibos. |
| RF33 | El sistema debe permitir registrar y actualizar las configuraciones tarifarias, incluyendo unidades, conceptos, tramos y fechas de vigencia, conservando las versiones utilizadas en cálculos anteriores. |
| RF34 | El sistema debe permitir al administrador activar o desactivar la evaluación de alertas de consumo, manteniendo definida la regla de comparación con el mes anterior. |
| RF35 | El sistema debe permitir al administrador consultar el estado de la integración y la última actualización de lecturas y recibos por suministro. |
| RF36 | El sistema debe registrar y mostrar las incidencias de integración con su fecha, suministro cuando sea identificable, motivo y estado de atención. |
| RF37 | El sistema debe permitir al administrador solicitar un nuevo intento de actualización o procesamiento de registros pendientes, conservando las validaciones y el control de duplicados. |
| RF38 | El sistema debe disponer de una fuente simulada que proporcione suministros, lecturas, tarifas y recibos para demostrar su funcionamiento sin depender de medidores inteligentes reales. |
| RF39 | El entorno de pruebas debe permitir representar accesos válidos e inválidos, consumo normal y elevado, datos faltantes, registros duplicados, disponibilidad de recibos y fallos de comunicación. |

## Relación entre historias de usuario y requisitos funcionales

| Historia de usuario | Requisitos funcionales relacionados |
|---------------------|-------------------------------------|
| HU01 — Acceder con código de suministro y DNI | RF01, RF02, RF03, RF04 |
| HU02 — Cerrar sesión | RF03 |
| HU03 — Consultar consumo y actualización | RF05, RF06, RF07, RF08, RF09, RF10, RF11, RF12, RF13, RF14 |
| HU04 — Consultar costo acumulado y tarifas | RF15, RF16 |
| HU05 — Consultar proyección mensual | RF17, RF18 |
| HU06 — Revisar meses anteriores | RF19 |
| HU07 — Comparar periodos mediante gráficos y estadísticas | RF20, RF21 |
| HU08 — Recibir alertas por superar el consumo anterior | RF22, RF24, RF26 |
| HU09 — Habilitar o deshabilitar notificaciones | RF23 |
| HU10 — Recibir notificaciones con Nivora cerrada | RF23, RF26 |
| HU11 — Consultar notificaciones y marcarlas como leídas | RF24, RF25 |
| HU12 — Consultar recibos y fechas límite | RF27, RF28 |
| HU13 — Recibir el aviso del recibo disponible | RF24, RF26, RF27, RF29 |
| HU14 — Acceder como administrador | RF30 |
| HU15 — Consultar suministros y registros de acceso | RF31 |
| HU16 — Configurar periodos mensuales | RF32 |
| HU17 — Gestionar tarifas y vigencias | RF33 |
| HU18 — Activar o desactivar la evaluación de alertas | RF34 |
| HU19 — Consultar el estado de la integración | RF35 |
| HU20 — Revisar incidencias y recuperar información pendiente | RF09, RF36, RF37 |
| HU21 — Utilizar el entorno de simulación | RF38, RF39 |