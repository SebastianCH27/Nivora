# Restricciones de Nivora

Las restricciones establecen condiciones que deben respetarse
al diseñar y desarrollar el sistema.

## Restricciones identificadas

| ID | Restricción | Descripción |
|----|-------------|-------------|
| RC01 | Acceso desde distintos dispositivos | Las interfaces deben adaptarse a celulares, laptops, computadoras y otros dispositivos compatibles, permitiendo utilizar las funciones de consulta. |
| RC02 | Control de versiones | El código y la documentación deben gestionarse mediante Git y GitHub. Los avances del laboratorio se registrarán en la rama main mediante commits y pushes. |
| RC03 | API REST propia | La comunicación entre las interfaces y el backend de Nivora debe realizarse mediante una API REST propia, compartiendo las reglas de negocio. |
| RC04 | Integración simulada con la empresa de agua | En esta primera etapa, los suministros, lecturas, tarifas y recibos deben obtenerse mediante una API simulada que represente al sistema de la empresa. La conexión real quedará para una etapa posterior. |
| RC05 | Dependencia de una API de identidad | La validación del DNI y la consulta de sus datos deben realizarse mediante una API de identidad, respetando sus formatos, mecanismos de acceso y condiciones de uso. |
| RC06 | Acceso con código y DNI | El acceso de consulta debe utilizar dos campos: código de suministro y DNI. Se aceptará cualquier persona que ingrese un código existente y un DNI válido, sin exigir que sea el titular del suministro. |
| RC07 | Notificaciones al dispositivo | El envío de avisos debe utilizar un mecanismo compatible con los dispositivos admitidos. La recepción dependerá del permiso de notificación, la conectividad y el servicio de entrega correspondiente. |
| RC08 | Actualización condicionada por la fuente | La información mostrada debe corresponder a las lecturas disponibles. La frecuencia de actualización se ajustará a la fuente simulada y, posteriormente, a las condiciones de la API de la empresa. |
| RC09 | Procedencia de los recibos | Nivora debe consultar los recibos proporcionados por la empresa, representados mediante datos simulados en esta etapa. Para la demostración, el plazo de pago será de una semana desde su disponibilidad. |
| RC10 | Separación de los sistemas externos | El desarrollo debe concentrarse en Nivora y su integración. Los medidores, la infraestructura de comunicación y los sistemas internos de la empresa permanecerán fuera de su responsabilidad. |

## Límites de esta primera etapa

- El monitoreo se concentrará en el consumo de agua potable.
- Los medidores inteligentes y la conexión real con la empresa
  serán representados mediante la simulación.
- Nivora permitirá consultar recibos y fechas límite de pago;
  el procesamiento de pagos se realizará fuera de la aplicación.
- Los costos acumulados y las proyecciones se mostrarán como estimaciones.
  El importe definitivo se obtendrá del recibo disponible.
- Las metas personales, recomendaciones, reportes descargables
  y el monitoreo de otros servicios quedarán para futuras ampliaciones.