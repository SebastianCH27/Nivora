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

## EQ-07. Interoperabilidad: intercambio con servicios externos

| Elemento | Descripción |
|---|---|
| **Atributo de calidad** | Interoperabilidad — AC07. |
| **Fuente** | Casos de uso de acceso, actualización de datos y envío de notificaciones de Nivora. |
| **Estímulo** | Solicitan validar un DNI, obtener suministros, lecturas, tarifas o recibos, o enviar un aviso al dispositivo. |
| **Artefacto** | Contratos de integración y adaptadores de agua, identidad y notificaciones. |
| **Entorno** | Pruebas con las API simuladas de empresa e identidad y un emisor ficticio de notificaciones. Se documentan los formatos de solicitudes, respuestas y errores. Se incluyen datos válidos, inexistentes, formatos incompatibles y fallos de comunicación. |
| **Respuesta esperada** | Los adaptadores generan las solicitudes correspondientes a cada servicio y transforman sus respuestas al modelo utilizado por Nivora. Distinguen los datos inexistentes de los errores de formato y comunicación, sin incorporar respuestas incompatibles como información válida. |
| **Medida** | El 100 % de los casos válidos definidos debe respetar los campos, tipos y unidades de sus contratos, cuando corresponda. El 100 % de los resultados negativos y errores simulados debe clasificarse correctamente. Debe haber cero respuestas incompatibles incorporadas como datos válidos. |
| **Verificación** | Ejecutar pruebas de contrato para agua, identidad y notificaciones. Comparar solicitudes, respuestas y transformaciones con los formatos documentados. Probar datos inexistentes, campos obligatorios ausentes, tipos incorrectos y tiempos de espera agotados. Revisar los resultados e incidencias generados. |
| **Estado** | Pendiente de completar la matriz de contratos y revisar las especificaciones de los proveedores reales antes de sustituir las simulaciones. |
| **Trazabilidad** | AC07; DA04 y DA07; RC04, RC05 y RC07; RF02, RF05, RF23–RF27 y RF38. |

RENIEC se contempla como integración de identidad del sistema futuro.
La demostración actual utiliza una API de identidad simulada
con personas ficticias.

Las pruebas con servicios simulados comprueban los contratos definidos
para la demostración. La compatibilidad con los servicios reales
deberá verificarse al implementar esas conexiones.

## EQ-08. Usabilidad: consultas claras desde distintos dispositivos

| Elemento | Descripción |
|---|---|
| **Atributo de calidad** | Usabilidad — AC08. |
| **Fuente** | Persona que utiliza Nivora desde un celular o una computadora. |
| **Estímulo** | Necesita consultar y comprender el consumo, costo estimado, proyección, recibos y avisos de un suministro, o interpretar un mensaje de error de acceso. |
| **Artefacto** | Interfaz de acceso, panel de consulta, recibos, notificaciones y mensajes de error. |
| **Entorno** | Prueba con datos ficticios, tareas previamente definidas y participantes que utilizan la interfaz sin instrucciones sobre los pasos a seguir. Se evalúan vistas de celular de 390 × 844 píxeles y escritorio de 1440 × 1000 píxeles. |
| **Respuesta esperada** | La interfaz presenta información legible, unidades y fechas identificables, y controles accesibles en ambas vistas. Permite distinguir el consumo, el costo estimado, la proyección y el importe definitivo del recibo. Los mensajes explican los errores de datos y los fallos de comunicación. |
| **Medida** | Como propuesta inicial, al menos 4 de 5 participantes deben completar las tareas definidas sin ayuda del observador e interpretar correctamente los importes y mensajes evaluados. En ambas vistas deben permanecer visibles o accesibles todos los controles necesarios, sin contenido esencial cortado ni desplazamiento horizontal de la página. |
| **Verificación** | Solicitar que los participantes accedan, consulten el consumo, distingan los importes, localicen un recibo y su vencimiento, y consulten un aviso. Presentar casos de datos inválidos y fallos de comunicación. Registrar tareas completadas, ayudas solicitadas, interpretaciones y dificultades en cada vista. |
| **Estado** | Pendiente de realizar la prueba con participantes y documentar los resultados. |
| **Trazabilidad** | AC08; RC01; RF01, RF02, RF11, RF16, RF18, RF25 y RF28. |

La muestra de cinco participantes y los tamaños de pantalla
son condiciones propuestas para organizar esta evaluación.
Los resultados se registrarán junto con las tareas y dispositivos utilizados.

## EQ-09. Consistencia entre dispositivos: resultados equivalentes

| Elemento | Descripción |
|---|---|
| **Atributo de calidad** | Consistencia entre dispositivos — AC09. |
| **Fuente** | Usuarios que consultan el mismo suministro desde distintos dispositivos. |
| **Estímulo** | Realizan consultas de consumo, costos, proyección, historial o recibos desde un celular y una computadora. |
| **Artefacto** | API REST propia, reglas de cálculo del backend e interfaces de consulta. |
| **Entorno** | Sesiones válidas asociadas al mismo suministro. Las comparaciones utilizan el mismo periodo, versión de lecturas, versión tarifaria y fecha de cálculo controlada. |
| **Respuesta esperada** | La API propia proporciona resultados calculados con las mismas reglas de negocio. Las interfaces muestran valores equivalentes de consumo, costos, proyecciones y recibos para las condiciones comparadas. |
| **Medida** | El 100 % de los casos definidos debe mostrar resultados equivalentes entre los dispositivos evaluados. Debe haber cero diferencias en los valores comparados después de normalizar las unidades y aplicar las mismas reglas de redondeo. Los periodos y fechas de lectura mostrados deben coincidir con los datos utilizados por el backend. |
| **Verificación** | Consultar el mismo suministro desde ambos dispositivos y registrar las respuestas de la API y los valores mostrados. Comparar consumo, costos, proyección, historial y recibos, identificando sus versiones y fechas. Comprobar la equivalencia entre litros y metros cúbicos cuando se utilicen unidades distintas. |
| **Estado** | Pendiente de ejecutar y documentar la comparación entre dispositivos. |
| **Trazabilidad** | AC09; DA06; RC01 y RC03; RF12, RF14, RF15, RF17, RF19 y RF28. |

Cada comparación identificará la versión de los datos y la fecha
de cálculo utilizada. Las diferencias de distribución visual
entre pantallas se evaluarán mediante el escenario de usabilidad.

## EQ-10. Mantenibilidad: sustitución del adaptador de agua

| Elemento | Descripción |
|---|---|
| **Atributo de calidad** | Mantenibilidad — AC10. |
| **Fuente** | Desarrollador encargado del mantenimiento de Nivora. |
| **Estímulo** | Necesita sustituir el adaptador de la empresa de agua por otra implementación que conserve el contrato de integración definido. |
| **Artefacto** | Contrato de la fuente de agua, adaptadores, composición del backend y pruebas automatizadas. |
| **Entorno** | Existe una versión de referencia con pruebas registradas. El nuevo adaptador proporciona los suministros, lecturas, tarifas y recibos requeridos por el mismo contrato. |
| **Respuesta esperada** | El cambio se concentra en el adaptador y su configuración. Las reglas del dominio y los casos de uso conservan su independencia respecto del formato y la tecnología del proveedor. Las consultas, actualizaciones y alertas mantienen el comportamiento esperado. |
| **Medida** | Aprobar el 100 % de las pruebas de contrato y regresión definidas para el cambio. Registrar cero violaciones de las reglas de dependencias y cero modificaciones del dominio o de los casos de uso motivadas únicamente por el formato o protocolo del proveedor, mientras se conserve el contrato. |
| **Verificación** | Realizar una sustitución controlada, revisar los archivos modificados y las importaciones, ejecutar las pruebas del nuevo adaptador y la regresión, y comprobar las reglas de capas. Comparar los resultados con la versión de referencia. |
| **Estado** | Pendiente de realizar la sustitución controlada y documentar los cambios y pruebas. |
| **Trazabilidad** | AC10; DA09 y DA04; ADR-002 y ADR-004. |

## Resumen de escenarios

| Código | Atributo de calidad |
|---|---|
| EQ-01 | Rendimiento |
| EQ-02 | Escalabilidad |
| EQ-03 | Exactitud e integridad |
| EQ-04 | Continuidad ante fallos externos |
| EQ-05 | Actualidad y trazabilidad |
| EQ-06 | Seguridad |
| EQ-07 | Interoperabilidad |
| EQ-08 | Usabilidad |
| EQ-09 | Consistencia entre dispositivos |
| EQ-10 | Mantenibilidad |