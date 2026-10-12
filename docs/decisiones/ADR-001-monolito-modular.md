# ADR-001: Adoptar un monolito modular para el backend de Nivora

- **Estado:** Aceptado para el diseño inicial.
- **Fecha:** 2026-10-11.

## Contexto

Nivora permite consultar el consumo de agua potable, el costo acumulado
estimado, la proyección mensual, el historial, los recibos y las
notificaciones de un suministro.

El acceso de consulta utiliza un código de suministro y un DNI,
validados mediante las integraciones correspondientes. No se exige
que la persona sea titular del suministro. Las operaciones
administrativas requieren un acceso diferenciado.

El sistema debe atender 5000 usuarios simultáneamente mientras
continúan las actualizaciones y la evaluación de alertas.
También debe permitir modificar tarifas, proyecciones e integraciones
sin afectar innecesariamente otras funciones.

Esta decisión responde a los siguientes drivers:

| Driver | Necesidad |
|--------|-----------|
| DA01 — Capacidad y rendimiento | Atender 5000 usuarios simultáneamente mientras continúan las actualizaciones y la evaluación de alertas. |
| DA09 — Mantenibilidad | Separar responsabilidades para facilitar cambios en tarifas, proyecciones, alertas y módulos. |
| DA05 — Continuidad ante fallos externos | Mantener las consultas de información almacenada cuando falle la API de la empresa. |
| DA06 — Consistencia entre dispositivos | Centralizar las validaciones y reglas de negocio mediante una API REST propia. |

## Decisión

Organizar el backend de Nivora como un monolito modular:
una aplicación desplegable que contiene módulos con
responsabilidades y límites definidos.

Los módulos serán:

| Módulo | Responsabilidad |
|--------|-----------------|
| Acceso y suministros | Validar el acceso, gestionar las sesiones de consulta y administrativas, registrar accesos y consultar suministros. |
| Lecturas, consumo e historial | Incorporar y validar lecturas, evitar duplicados, calcular el consumo, organizar periodos y preparar el historial y las estadísticas. |
| Tarifas, costos y proyecciones | Gestionar configuraciones tarifarias y calcular costos estimados y proyecciones mensuales. |
| Alertas y notificaciones | Evaluar alertas, registrar avisos, gestionar dispositivos asociados y coordinar las entregas. |
| Recibos | Obtener los recibos proporcionados por la empresa y presentar sus importes, disponibilidad y fechas límite. |

Los módulos se comunicarán mediante interfaces definidas,
respetando sus responsabilidades y evitando acceder directamente
a los detalles internos de otros módulos.

Las interfaces de consulta y administración utilizarán
la API REST propia del backend.

La comunicación con la empresa de agua, la API de identidad
y el servicio de notificaciones se organizará mediante adaptadores.

Las consultas utilizarán el almacenamiento propio de Nivora.
La incorporación de datos externos y la entrega de notificaciones
se ejecutarán mediante tareas controladas que no dependan
de mantener abierta la interfaz del usuario.

## Justificación

Un monolito modular permite mantener un despliegue inicial sencillo
y establecer límites entre las responsabilidades del sistema.

La organización modular facilita localizar las reglas,
probar componentes y realizar cambios en las funcionalidades
y sus integraciones.

Centralizar los cálculos y las validaciones en el backend
permite proporcionar resultados consistentes desde distintos dispositivos.

Separar las consultas de la actualización externa permite
seguir mostrando información almacenada cuando la empresa no responde,
indicando su fecha y estado de actualización.

Si las mediciones justifican ampliar la capacidad, se podrán
desplegar varias instancias del backend. Esto requerirá
almacenamiento y sesiones compartidos, además de coordinación
de las tareas de actualización y notificación.

La elección de este estilo no demuestra por sí sola
el cumplimiento de la capacidad de 5000 usuarios.

## Consecuencias

- El backend se desplegará como una unidad.
- Los módulos deberán respetar sus límites e interfaces.
- El escalamiento horizontal replicará el backend completo.
- Las tareas de actualización y entrega deberán evitar
  procesamientos concurrentes indebidos y avisos duplicados.
- Las lecturas y operaciones deberán conservar sus validaciones
  y controles de duplicados al ampliar la infraestructura.
- La capacidad y los tiempos de respuesta deberán verificarse
  mediante las pruebas definidas en los atributos de calidad.
- Una separación futura en servicios requerirá una nueva evaluación
  y decisiones arquitectónicas que la justifiquen.

## Alternativa considerada

Los microservicios permitirían desplegar y escalar
distintas responsabilidades de forma independiente.

Sin embargo, añadirían comunicación distribuida, coordinación
entre servicios, tratamiento de fallos y varios despliegues.

Para la etapa inicial del proyecto se adopta un monolito modular,
manteniendo límites que permitan evaluar su evolución posteriormente.

## Relación con el boilerplate

El boilerplate propio de Nivora contiene una API Express,
casos de aplicación, reglas de dominio y adaptadores separados.

Esta base permite estudiar las dependencias internas
y demostrar varios flujos del sistema. Los módulos de este ADR
describen la organización funcional objetivo; el ejemplo
todavía no implementa todos los requisitos.

El almacenamiento JSON y las sesiones en memoria son
adaptadores locales para una sola instancia. Deberán sustituirse
antes de un despliegue que requiera varias instancias.

El simulador de la empresa y de identidad representa
sistemas externos. Aunque se inicia junto con el ejemplo
por comodidad, no forma parte de los módulos de negocio de Nivora.