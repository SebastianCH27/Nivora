# Alcance del boilerplate frente a los requisitos de Nivora

Los requisitos del repositorio describen el sistema objetivo. Esta base
ejecutable demuestra su organización y varios flujos; no cambia ni elimina
los requisitos pendientes.

| Requisitos | Evidencia del ejemplo | Límite o trabajo pendiente |
|------------|----------------------|---------------------------|
| RF01–RF04 | Dos campos, consulta de APIs locales, sesión y registro de acceso | API oficial de identidad, proveedor y condiciones reales pendientes; el DNI utilizado no demuestra presencia física de su titular |
| RF05–RF09 | Obtención HTTP automática, registro, validación, deduplicación y recuperación | Más formatos, cambios de medidor y gestión operativa de incidencias |
| RF10–RF14 | Reglas para acumulados e intervalos; panel de acumulados, unidades, cobertura y fechas | Más escenarios de intervalos, cortes de periodos y datos incompletos de fuentes reales |
| RF15–RF18 | Tramos, conceptos, estimación y proyección comunes en el backend | Cambios de tarifa dentro del mes y reglas reales de la empresa |
| RF19–RF21 | Historial mensual, gráfico y comparación con fechas y diferencias | Selección arbitraria de periodos y gráficos con intervalos más detallados |
| RF22, RF24–RF25 | Alerta por superar el total anterior, deduplicación de eventos y estado leído por suministro | Preferencias individuales y mayor diversidad de eventos |
| RF23, RF26 | Suscripción, baja, adaptador Web Push, service worker y reintentos | Configurar VAPID y comprobar recepción con la aplicación cerrada en dispositivos compatibles |
| RF27–RF29 | Recibos obtenidos de la API ficticia y avisos con plazo de una semana | API real y documentación del formato real de los recibos |
| RF30–RF31 | Administración con credenciales separadas; vista de suministros y accesos | Gestión de usuarios administrativos y permisos más detallados |
| RF32 | Reglas de periodos mensuales con límites en Lima | Pantalla y casos de configuración administrativa pendientes |
| RF33 | Configuración de tarifa por tramos y conservación de versiones recibidas | Alta y edición administrativa, conflictos entre versiones y vigencias dentro del mes pendientes |
| RF34–RF37 | Activar alertas, ver integración e incidencias y reintentar | Atención manual de incidencias, métricas por suministro y recuperación operativa avanzada |
| RF38–RF39 | API de suministros, lecturas, tarifas y recibos; casos normales, elevados, inválidos, incompletos, duplicados y caídas | Ampliar matriz de escenarios y pruebas de carga |

## Calidad

La exactitud, la deduplicación, los límites de consulta y la continuidad
ante fallos se comprueban mediante pruebas automatizadas específicas.
La interfaz se adapta a pantallas de escritorio y celular.

El repositorio JSON y las sesiones en memoria son adaptadores locales de
una sola instancia. El ejemplo no acredita disponibilidad de producción,
seguridad completa, escalamiento horizontal ni el objetivo de 5000 usuarios.
No incorpora caché de consultas: primero corresponde medir y justificar
su necesidad y semántica de invalidación.

La conexión local utiliza HTTP. HTTPS y cookies seguras deben configurarse
en el despliegue correspondiente. Las consultas de APIs locales de identidad
y empresa representan contratos; su sustitución real sigue pendiente.

La prueba reducida del generador es evidencia de funcionamiento de esa
herramienta. El perfil de 5000 usuarios durante 30 minutos continúa siendo
una meta de evaluación del proyecto.
