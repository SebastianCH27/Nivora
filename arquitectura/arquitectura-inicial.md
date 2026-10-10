# Arquitectura inicial de Nivora

## Arquitectura en tres capas

Nivora utilizará un backend modular con reglas de negocio compartidas
por las interfaces de consulta y administración.

| Capa | Elementos | Responsabilidad |
|------|-----------|-----------------|
| Presentación | Interfaces adaptadas a distintos dispositivos y API REST propia | Mostrar información, recibir las acciones del usuario y entregar sus solicitudes al backend. La API REST se ejecutará en el backend y será su interfaz de entrada. |
| Lógica de negocio | Acceso y suministros; lecturas, consumo e historial; tarifas, costos y proyecciones; alertas y notificaciones; recibos | Validar el acceso, procesar lecturas, aplicar las reglas de cálculo y coordinar las operaciones de Nivora. |
| Datos | Componentes de acceso a datos y base de datos propia | Almacenar y recuperar sesiones, permisos administrativos, suministros, lecturas, tarifas, resultados, dispositivos, recibos, notificaciones, registros de acceso e incidencias. |

## Módulos de la capa de negocio

| Módulo | Responsabilidad | Requisitos relacionados |
|--------|----------------|-------------------------|
| Acceso y suministros | Validar el código de suministro y el DNI mediante la integración correspondiente; gestionar sesiones de consulta y administrativas; registrar accesos y permitir consultar los suministros reconocidos. | RF01–RF04, RF30, RF31 |
| Lecturas, consumo e historial | Obtener y validar lecturas, controlar duplicados, calcular el consumo, organizar periodos, preparar el historial y las estadísticas y supervisar la actualización de información. | RF05–RF14, RF19–RF21, RF32, RF35–RF37 |
| Tarifas, costos y proyecciones | Gestionar tarifas y vigencias, calcular el costo acumulado estimado y proyectar el consumo y costo al finalizar el mes. | RF15–RF18, RF33 |
| Alertas y notificaciones | Evaluar la comparación con el mes anterior, registrar avisos, gestionar los dispositivos asociados y coordinar el envío y la consulta de notificaciones. | RF22–RF26, RF34 |
| Recibos | Obtener y organizar los recibos disponibles, presentar sus importes definitivos y determinar la fecha límite del plazo de una semana utilizado en la demostración. Comunicar su disponibilidad al módulo de notificaciones. | RF27–RF29 |

## Componentes de integración

Los módulos de negocio utilizarán componentes de integración
para comunicarse con los sistemas externos.

| Componente | Sistema externo | Función |
|------------|-----------------|---------|
| Integración con la empresa de agua | API simulada de la empresa | Obtener suministros, lecturas, tarifas y recibos, adaptando los datos al formato de Nivora y gestionando los errores de comunicación. |
| Integración de identidad | API de validación del DNI | Consultar la existencia del DNI y obtener los datos asociados al documento. |
| Integración de notificaciones | Servicio de notificaciones | Enviar los avisos a los dispositivos asociados al suministro que tengan habilitado el permiso correspondiente. |

La fuente simulada y sus escenarios de prueba permitirán verificar
los requisitos RF38 y RF39.

En una etapa posterior, el componente de integración con la empresa
podrá adaptarse a su API real, conservando las reglas internas
que correspondan al contrato de datos.

## Relación entre las capas y módulos

1. La persona realiza una acción desde la interfaz de Nivora.
2. La API REST recibe la solicitud y la dirige al módulo correspondiente.
3. La lógica de negocio comprueba la sesión, los permisos y los parámetros
   necesarios para ejecutar la operación.
4. El módulo utiliza los componentes de acceso a datos para consultar
   o actualizar la información.
5. El backend devuelve el resultado y la interfaz lo presenta.

Durante el inicio de sesión, el módulo de acceso utilizará las integraciones
para validar el suministro y consultar el DNI. La sesión se creará
únicamente cuando las comprobaciones sean satisfactorias.

La incorporación de nuevas lecturas permitirá actualizar el consumo,
los costos estimados, las proyecciones y las condiciones de alerta.

Cuando se incorpore un recibo disponible, el módulo de recibos
comunicará el evento al módulo de alertas y notificaciones
para generar y enviar el aviso correspondiente.

## Actualización y continuidad

Las consultas utilizarán la información almacenada en Nivora.
La actualización desde la empresa se realizará mediante un proceso
controlado, según la frecuencia definida para la fuente simulada.

Se propone ejecutar la actualización de lecturas y el envío de avisos
en segundo plano, para que estas tareas no bloqueen las consultas.

Si la API de la empresa falla, las personas podrán consultar
la información almacenada, acompañada de su fecha y estado de actualización.
Las lecturas pendientes se recuperarán al restablecerse la comunicación,
manteniendo las validaciones y el control de duplicados.

## Capacidad del sistema

La solución deberá atender 5000 usuarios simultáneamente mientras
continúan las actualizaciones y la evaluación de alertas.

La cantidad de instancias del backend y los recursos de procesamiento,
memoria y almacenamiento se determinarán mediante pruebas.

La organización en tres capas permite separar responsabilidades;
la capacidad requerida deberá comprobarse con la implementación
y las metas documentadas en los atributos de calidad.