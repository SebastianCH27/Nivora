# ADR-002: Adoptar Clean Architecture para Nivora

- **Estado:** Aceptado para el diseño inicial.
- **Fecha:** 2026-10-11.

## Contexto

Nivora necesita mantener reglas comunes para validar lecturas,
calcular consumos, aplicar tarifas, proyectar costos y evaluar alertas.

Estas reglas deben producir resultados consistentes desde
las distintas interfaces que utilizan la API propia.

La integración con la empresa de agua será simulada inicialmente.
Posteriormente podrá sustituirse por una integración real,
adaptando sus formatos y condiciones de comunicación.

El almacenamiento y el mecanismo de notificaciones también
podrán cambiar durante la evolución del proyecto.

Esta decisión responde a los siguientes drivers:

| Driver | Necesidad |
|--------|-----------|
| DA09 — Mantenibilidad | Facilitar cambios en tarifas, proyecciones, alertas y módulos, controlando sus dependencias. |
| DA04 — Sustitución de la integración simulada | Separar la fuente externa de las interfaces y las reglas de negocio. |
| DA02 — Exactitud e integridad | Mantener reglas de validación y cálculo comunes, con control de duplicados y trazabilidad. |
| DA06 — Consistencia entre dispositivos | Centralizar las reglas utilizadas por las consultas mediante la API propia. |

## Decisión

Adoptar Clean Architecture para organizar las dependencias
internas del backend de Nivora.

Esta decisión complementa el monolito modular definido en ADR-001.
El monolito modular establece la organización global del backend;
Clean Architecture establece la separación de responsabilidades
y la dirección de sus dependencias internas.

Se utilizarán cuatro capas:

| Capa | Responsabilidad |
|------|-----------------|
| Dominio | Contener las reglas de validación, consumo, periodos, costos, proyección y comparación para alertas, además de los contratos necesarios. |
| Aplicación | Coordinar los casos de uso de acceso, consultas, actualización, notificaciones y administración mediante los contratos del núcleo. |
| Infraestructura | Implementar los contratos de almacenamiento, sesiones e integración con las API externas y el servicio de notificaciones. |
| Presentación | Recibir solicitudes, interpretar sus parámetros, ejecutar los casos de uso y presentar resultados mediante la API y la interfaz web. |

## Regla de dependencias

Las dependencias del núcleo no apuntarán hacia las capas externas.

Se establecen las siguientes reglas:

- El dominio no importará Express, acceso a archivos,
  clientes HTTP ni servicios de notificaciones.
- La aplicación utilizará el dominio y los contratos del núcleo,
  sin depender de implementaciones concretas de infraestructura.
- La infraestructura implementará los contratos definidos
  en el dominio.
- La presentación utilizará los casos de aplicación
  y los mecanismos propios de la interfaz.
- Los componentes externos se seleccionarán y conectarán
  en la raíz de composición y el arranque del sistema.

El dominio y la aplicación podrán utilizar las capacidades
del lenguaje JavaScript sin incorporar dependencias
de los frameworks o servicios externos.

## Contratos y adaptadores

Los contratos expresarán las operaciones que necesita el núcleo.

En el boilerplate se encuentran en
`src/dominio/contratos/puertos.js` y representan:

- La fuente de suministros, lecturas, tarifas y recibos.
- La fuente de validación y consulta del DNI.
- El repositorio propio de Nivora.
- El gestor de sesiones.
- El emisor de notificaciones.

Los adaptadores se ubicarán en infraestructura.

La adaptación de formatos, los detalles de comunicación
y los mecanismos técnicos de acceso pertenecerán
a estas implementaciones externas.

Una integración real deberá respetar el contrato utilizado
por los casos de uso. Si sus datos tienen otra estructura,
el adaptador deberá transformarlos al formato requerido por Nivora.

## Justificación

La separación permite probar las reglas de negocio
sin iniciar la interfaz web ni depender de los servicios externos.

Mantener los cálculos en el núcleo evita que la pantalla
y otros consumidores de la API implementen versiones
diferentes de las mismas reglas.

Los contratos permiten cambiar una implementación
de almacenamiento o integración conservando los casos de uso,
si la nueva implementación satisface las mismas operaciones
y condiciones del contrato.

La separación también facilita identificar si un cambio
corresponde a una regla, un caso de uso, una integración
o una interfaz.

## Consecuencias

- Será necesario mantener contratos explícitos y adaptadores.
- Las reglas no deberán duplicarse en la interfaz web.
- Los casos de aplicación coordinarán las operaciones,
  mientras que el dominio determinará qué datos y cálculos son válidos.
- La selección de implementaciones concretas permanecerá
  fuera del dominio y de la aplicación.
- Los cambios en contratos requerirán revisar sus consumidores
  e implementaciones.
- Las pruebas deberán comprobar las reglas y el comportamiento
  de los adaptadores.
- La revisión de importaciones ayudará a detectar dependencias
  indebidas, pero no sustituirá la revisión completa del diseño.
- Esta organización no demuestra por sí sola el cumplimiento
  de la capacidad de 5000 usuarios ni la integración con servicios reales.

## Alternativa considerada

Una organización tradicional podría permitir que la lógica
de negocio dependiera directamente de componentes de datos
y servicios externos.

Esa dependencia haría que cambios en almacenamiento
o integraciones afectaran a las reglas y dificultaran
su ejecución aislada.

Se adopta Clean Architecture para mantener el núcleo
independiente de esas implementaciones.

## Relación con el boilerplate

El boilerplate propio de Nivora utiliza las carpetas:

- `src/dominio`
- `src/aplicacion`
- `src/infraestructura`
- `src/presentacion`

`src/app.js` conecta los casos de uso con sus adaptadores.
`src/server.js` prepara el almacenamiento, inicia los servidores
y organiza las tareas periódicas de actualización y entrega.

Las pruebas de dominio se ejecutan sin navegador ni servidor.
Las pruebas de integración verifican la API y su comunicación
con los servicios locales simulados.

La ejecución de `npm run verificar` comprobó que los 11 archivos
del núcleo revisados solo importan las capas permitidas.

Esta comprobación respalda la separación inicial,
sin acreditar que todos los requisitos estén implementados
ni que todos los límites arquitectónicos estén verificados.