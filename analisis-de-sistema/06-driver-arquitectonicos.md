# Drivers arquitectónicos de Nivora

Los drivers arquitectónicos son las necesidades y condiciones
que influyen significativamente en las decisiones de arquitectura.

Se identifican a partir de los requisitos funcionales, los atributos
de calidad y las restricciones del sistema.

## Drivers identificados

| ID | Driver arquitectónico | Origen | ¿Por qué influye en la arquitectura? |
|----|-----------------------|--------|-------------------------------------|
| DA01 | Atender 5000 usuarios simultáneamente mientras continúan las actualizaciones y la evaluación de alertas. | AC01 — Rendimiento; AC02 — Escalabilidad | Influye en el dimensionamiento del sistema, la atención de consultas y la estrategia para ampliar su capacidad. Los recursos necesarios deberán determinarse mediante pruebas. |
| DA02 | Mantener cálculos correctos y evitar el doble conteo de lecturas. | AC03 — Exactitud e integridad; RF07, RF08, RF10, RF15 | Requiere validaciones, control de duplicados y reglas de cálculo comunes, conservando la relación entre los resultados y los datos utilizados. |
| DA03 | Validar el acceso con código de suministro y DNI, y separar las operaciones administrativas. | AC06 — Seguridad; RC05, RC06; RF01–RF04, RF30 | Condiciona la gestión de sesiones, la integración con la API de identidad y los controles de acceso a las consultas y funciones administrativas. |
| DA04 | Utilizar una API simulada de la empresa de agua y permitir su sustitución por una integración real en una etapa posterior. | RC04 — Integración simulada; AC07 — Interoperabilidad; AC10 — Mantenibilidad; RF38 | Influye en la separación del componente de integración respecto de las interfaces y las reglas de negocio, para adaptar la fuente de datos sin modificar innecesariamente otras funciones. |
| DA05 | Mantener disponibles las consultas almacenadas cuando falle la API de la empresa. | AC04 — Continuidad ante fallos externos; AC05 — Actualidad y trazabilidad; RF09, RF14 | Requiere almacenamiento propio y una separación entre la actualización externa y las consultas. También debe conservarse el estado de actualización y recuperarse la información pendiente. |
| DA06 | Proporcionar resultados consistentes desde distintos dispositivos mediante una API REST propia. | RC01 — Acceso desde distintos dispositivos; RC03 — API REST propia; AC09 — Consistencia entre dispositivos | Influye en la separación entre interfaces y backend, centralizando las validaciones y reglas de negocio utilizadas por las consultas. |
| DA07 | Enviar notificaciones al dispositivo aunque Nivora esté cerrada. | RF23–RF26, RF29; RC07 — Notificaciones al dispositivo | Requiere asociar dispositivos con suministros, conservar los avisos generados e integrar un servicio de entrega. El envío debe funcionar sin depender de que el usuario mantenga abierta la aplicación. |
| DA08 | Consultar recibos proporcionados por la empresa y notificar su disponibilidad y fecha límite de pago. | RF27–RF29; RC09 — Procedencia de los recibos | Influye en el almacenamiento de recibos, el tratamiento de sus fechas y la generación de avisos. También exige distinguir el importe definitivo de las estimaciones calculadas por Nivora. |

## Aplicación en el diseño

Estos drivers orientarán la organización de las capas y módulos,
la integración con servicios externos, el almacenamiento de información
y el procesamiento de actualizaciones y notificaciones.

La selección de tecnologías y recursos deberá justificarse
con estas necesidades y con los resultados de las pruebas.