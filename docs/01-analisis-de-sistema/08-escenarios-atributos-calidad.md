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