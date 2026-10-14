# Escenarios de atributos de calidad de Nivora

Los escenarios concretan los atributos de calidad del sistema
y establecen cómo se comprobará su cumplimiento.

Las consultas utilizan el almacenamiento propio de Nivora.
La actualización de datos externos y la entrega de notificaciones
se ejecutan por separado, conforme a ADR-003.

## EQ-01. Rendimiento: consultas simultáneas

| Elemento | Descripción |
|---|---|
| **Atributo de calidad** | Rendimiento — AC01. |
| **Fuente** | Usuarios que consultan Nivora desde dispositivos compatibles. |
| **Estímulo** | 5000 usuarios activos con sesiones independientes realizan consultas de consumo, costos estimados, proyección, historial, recibos y notificaciones. |
| **Artefacto** | API REST propia, casos de uso de consulta y almacenamiento de Nivora. |
| **Entorno** | Prueba con infraestructura, datos y mezcla de operaciones documentados. La carga aumenta gradualmente y después se mantiene durante 30 minutos. Continúan la actualización de lecturas mediante la API simulada y la evaluación de alertas. |
| **Respuesta esperada** | Nivora atiende las consultas y devuelve la información almacenada correspondiente al suministro, mientras continúan las actualizaciones y la evaluación de alertas. |
| **Medida** | Mantener 5000 usuarios activos simultáneamente durante 30 minutos. Al menos el 95 % de las solicitudes válidas de cada operación principal debe responder en un máximo de 2 segundos. Menos del 1 % de las solicitudes válidas de cada operación debe fallar por errores internos o tiempos de espera agotados. |
| **Verificación** | Ejecutar la prueba de carga y registrar usuarios activos, duración, solicitudes, tiempos por operación, errores y uso de recursos. Comprobar que continúen las actualizaciones y la evaluación de alertas. Separar los rechazos esperados por datos inválidos de los errores internos. |
| **Estado** | Pendiente de ejecutar y documentar la prueba completa. |
| **Trazabilidad** | AC01 y DA01. |

Los 5000 usuarios simultáneos constituyen un requisito del proyecto.
La duración, los tiempos de respuesta y el porcentaje de errores
son las metas iniciales documentadas en los atributos de calidad.
Su cumplimiento deberá comprobarse mediante pruebas.

## EQ-02. Escalabilidad: crecimiento de usuarios

| Elemento | Descripción |
|---|---|
| **Atributo de calidad** | Escalabilidad — AC02. |
| **Fuente** | Usuarios que utilizan Nivora simultáneamente. |
| **Estímulo** | Aumenta progresivamente la cantidad de usuarios activos mientras continúan las actualizaciones de lecturas y la evaluación de alertas. |
| **Artefacto** | Backend de Nivora, API REST propia y almacenamiento. |
| **Entorno** | Entorno de prueba con datos, operaciones y configuraciones de recursos documentados. Se utiliza la API simulada de la empresa y se comparan una configuración inicial y otra con recursos ampliados. |
| **Respuesta esperada** | La ampliación de recursos permite atender una carga sostenible mayor, manteniendo los límites establecidos de respuesta y errores. |
| **Medida** | Como propuesta de evaluación, aumentar la carga por etapas de 500, 1000, 2500 y 5000 usuarios activos. La configuración ampliada debe sostener una carga mayor que la inicial. La etapa de 5000 usuarios debe mantenerse durante 30 minutos, con al menos el 95 % de las solicitudes válidas de cada operación principal respondidas en un máximo de 2 segundos y menos del 1 % de errores internos o tiempos de espera agotados. |
| **Verificación** | Registrar la carga sostenible, los tiempos por operación, los errores y el uso de recursos en cada configuración. Comparar los resultados manteniendo la misma mezcla de operaciones y condiciones de datos. Comprobar que continúen las actualizaciones y la evaluación de alertas. |
| **Estado** | Pendiente de definir las configuraciones de recursos y ejecutar la comparación de capacidad. |
| **Trazabilidad** | AC02 y DA01. |

Los escalones de carga constituyen una propuesta para organizar la prueba.
La capacidad de 5000 usuarios simultáneos conserva su condición
de requisito del proyecto.

## EQ-03. Exactitud e integridad: procesamiento de lecturas

| Elemento | Descripción |
|---|---|
| **Atributo de calidad** | Exactitud e integridad — AC03. |
| **Fuente** | API simulada de la empresa de agua. |
| **Estímulo** | Durante una actualización o un reintento llegan lecturas válidas, duplicadas, inválidas o incompatibles con los datos necesarios para calcular el consumo. |
| **Artefacto** | Validación e incorporación de lecturas, almacenamiento y reglas de cálculo de consumo, costos y proyecciones. |
| **Entorno** | Prueba con lecturas previamente almacenadas, tarifas versionadas, periodos definidos y una fecha de cálculo controlada. Se dispone de casos con resultados esperados calculados de forma independiente. |
| **Respuesta esperada** | Nivora valida las lecturas, evita duplicados y registra las incidencias. Calcula utilizando datos compatibles y tarifas aplicables. Cuando la información sea insuficiente, lo comunica. Si un identificador existente llega con valores diferentes, conserva la lectura aceptada y registra el conflicto para revisión. |
| **Medida** | El 100 % de los casos definidos debe coincidir con los resultados esperados de consumo, costo y proyección, según la precisión y el redondeo especificados para cada caso. Debe haber cero dobles conteos, cero lecturas inválidas utilizadas en cálculos y cero reemplazos silenciosos por identificadores en conflicto. |
| **Verificación** | Ejecutar casos de lecturas acumuladas e intervalos, duplicados, valores inválidos, datos incompletos y tarifas incompatibles. Comparar con cálculos independientes. Reenviar las mismas lecturas y comprobar que los resultados se mantengan para el mismo periodo, tarifa y fecha de cálculo. Revisar las incidencias registradas. |
| **Estado** | Pendiente de completar y documentar la matriz de casos y sus resultados. |
| **Trazabilidad** | AC03; DA02; RF07, RF08, RF10, RF13 y RF15–RF18. |

## EQ-04. Continuidad ante fallos externos

| Elemento | Descripción |
|---|---|
| **Atributo de calidad** | Continuidad ante fallos externos — AC04. |
| **Fuente** | API de la empresa de agua y servicio de entrega de notificaciones. |
| **Estímulo** | La API de la empresa deja de responder o el servicio de notificaciones presenta fallos temporales. |
| **Artefacto** | Actualización de datos, consultas sobre el almacenamiento propio y procesamiento de notificaciones de Nivora. |
| **Entorno** | Existen sesiones válidas, información previamente almacenada y avisos generados. Se provocan fallos externos por separado y simultáneamente, manteniendo Nivora en funcionamiento. |
| **Respuesta esperada** | Las sesiones vigentes continúan consultando la información almacenada. Nivora indica el fallo y la fecha de la última lectura. Conserva los avisos cuyo envío falla, registra las incidencias y realiza reintentos controlados. Cuando se recuperan los servicios, incorpora los datos pendientes y reintenta los envíos que correspondan. |
| **Medida** | El 100 % de las consultas válidas sobre información almacenada incluidas en la prueba debe responder durante el fallo. Debe haber cero pérdidas de datos aceptados y de avisos generados. Todos los suministros afectados deben mostrar su estado de actualización y la fecha de la última lectura disponible. La recuperación debe producir cero dobles conteos de lecturas y cero avisos duplicados por el mismo evento. |
| **Verificación** | Interrumpir la API simulada y provocar fallos del emisor de notificaciones. Consultar los datos almacenados y revisar fechas, estados, incidencias y avisos pendientes. Restablecer los servicios y comprobar la recuperación de registros y los reintentos, sin duplicados ni bloqueo de las consultas. |
| **Estado** | Pendiente de ejecutar y documentar la matriz completa de fallos y recuperación. |
| **Trazabilidad** | AC04; DA05 y DA07; RF09, RF14 y RF24–RF26; ADR-003 y ADR-004. |

La continuidad de las consultas se aplica a sesiones válidas.
Si un nuevo acceso necesita una API que no responde, Nivora debe
informar que no pudo completar la validación antes de crear la sesión.

La aceptación de un envío por el proveedor no demuestra que
la notificación haya aparecido en la pantalla del dispositivo.

## EQ-05. Actualidad y trazabilidad: procedencia de los resultados

| Elemento | Descripción |
|---|---|
| **Atributo de calidad** | Actualidad y trazabilidad — AC05. |
| **Fuente** | Usuario de Nivora. |
| **Estímulo** | Consulta el consumo, costo estimado o proyección después de una actualización de lecturas. |
| **Artefacto** | Registro de lecturas, tarifas y periodos; reglas de cálculo y servicios de consulta. |
| **Entorno** | Existen lecturas identificadas, tarifas versionadas y periodos definidos. La API simulada proporciona nuevos registros y la actualización se realiza por separado de las consultas. Las fechas de medición y recepción están controladas en la prueba. |
| **Respuesta esperada** | Nivora muestra la fecha de la última lectura aceptada y el estado de actualización. Cada resultado puede relacionarse con las lecturas, versión tarifaria y periodo utilizados. Los costos estimados y las proyecciones identifican sus fechas de cálculo y los supuestos correspondientes. |
| **Medida** | En el 100 % de los casos definidos, la fecha mostrada debe corresponder a la última lectura aceptada del suministro. Todos los resultados revisados deben conservar referencias verificables a sus datos de origen, con cero referencias ausentes o incompatibles. La primera consulta posterior a una actualización completada debe reflejar los datos aceptados que correspondan a su periodo. |
| **Verificación** | Incorporar lecturas con fechas de medición y recepción diferentes. Consultar los resultados antes y después de la actualización. Comparar fechas, periodos, versiones tarifarias y registros utilizados. Reconstruir los cálculos revisados a partir de esas referencias y comprobar los supuestos de las proyecciones. |
| **Estado** | Pendiente de completar los casos y documentar la comprobación de fechas y referencias. |
| **Trazabilidad** | AC05; DA05; RF06, RF12, RF14, RF16 y RF18. |

La actualidad se evalúa respecto de las lecturas disponibles y aceptadas,
según la frecuencia de actualización de la fuente.
La fecha de medición, la fecha de recepción y la fecha de consulta
representan momentos distintos.

## EQ-06. Seguridad: sesiones y operaciones protegidas

| Elemento | Descripción |
|---|---|
| **Atributo de calidad** | Seguridad — AC06. |
| **Fuente** | Usuario sin sesión válida o con permisos insuficientes. |
| **Estímulo** | Intenta acceder a una operación protegida, ejecutar una función administrativa o consultar registros de acceso sin autorización. |
| **Artefacto** | API REST propia, gestión de sesiones, controles de permisos y acceso a registros e información de identidad. |
| **Entorno** | Pruebas con sesiones de consulta y administrativas, además de sesiones ausentes, inválidas, caducadas o cerradas. La protección del transporte se comprueba en un despliegue de pruebas configurado con HTTPS. |
| **Respuesta esperada** | Nivora comprueba la sesión y los permisos antes de ejecutar la operación. Rechaza los intentos no autorizados, conserva los datos sin modificaciones indebidas y protege la información del DNI y los registros de acceso. Las operaciones autorizadas funcionan según el perfil correspondiente. |
| **Medida** | Rechazar el 100 % de los intentos no autorizados definidos en la prueba, con cero modificaciones indebidas y cero exposiciones no autorizadas de datos del DNI o registros de acceso. Permitir el 100 % de los casos autorizados definidos. Todas las comunicaciones protegidas del despliegue evaluado deben utilizar HTTPS. |
| **Verificación** | Probar los distintos estados de sesión y ambos perfiles. Intentar operaciones administrativas desde una sesión de consulta y revisar sus respuestas y efectos. Comprobar el cierre y la caducidad de sesiones, la protección de los registros y el uso de HTTPS en el despliegue evaluado. |
| **Estado** | Pendiente de completar la matriz de sesiones y permisos y verificar el despliegue HTTPS. |
| **Trazabilidad** | AC06; DA03; RF01–RF04, RF30 y RF31; RC05 y RC06. |

El acceso de consulta requiere un suministro existente y un DNI válido,
sin exigir titularidad. Las operaciones administrativas utilizan
un acceso diferenciado y sus propios permisos.

La demostración local utiliza HTTP. La comprobación de HTTPS
corresponde al despliegue de pruebas configurado para ese propósito.