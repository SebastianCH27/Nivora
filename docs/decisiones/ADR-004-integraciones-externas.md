# ADR-004: Integrar servicios externos mediante contratos y adaptadores

- **Estado:** Aceptado para el diseño inicial.
- **Fecha:** 2026-10-11.

## Contexto

Nivora necesita comunicarse con tres servicios externos:

- La empresa de agua, para obtener suministros, lecturas,
  tarifas y recibos.
- Una API de identidad, para validar el DNI
  y consultar los datos asociados al documento.
- Un servicio de notificaciones, para entregar avisos
  a los dispositivos asociados al suministro.

Cada servicio puede utilizar formatos, credenciales,
mecanismos de comunicación y respuestas diferentes.

La integración con la empresa será simulada inicialmente.
La API real de identidad todavía debe seleccionarse.
Las notificaciones necesitan funcionar sin depender
de que la interfaz permanezca abierta.

Esta decisión responde a los siguientes drivers:

| Driver | Necesidad |
|--------|-----------|
| DA04 — Integración sustituible | Utilizar una fuente simulada de la empresa y permitir una integración real posterior. |
| DA03 — Validación del acceso | Validar el suministro y el DNI antes de crear una sesión. |
| DA07 — Notificaciones al dispositivo | Entregar avisos aunque la aplicación esté cerrada, según las condiciones del dispositivo y del servicio. |
| DA08 — Recibos proporcionados por la empresa | Obtener los importes definitivos y las fechas del recibo desde la fuente correspondiente. |

## Decisión

Definir contratos en el dominio para expresar
las operaciones externas que necesita Nivora.

Implementar esos contratos mediante adaptadores
ubicados en infraestructura.

Los casos de aplicación utilizarán los contratos
sin conocer las credenciales, bibliotecas o formatos
particulares de cada proveedor.

Las implementaciones concretas se seleccionarán
en la composición y configuración del sistema.

## Contratos de integración

| Contrato | Operaciones necesarias |
|----------|------------------------|
| Fuente de agua | Consultar un suministro y obtener lecturas, tarifas y recibos disponibles. |
| Fuente de identidad | Consultar un DNI y devolver sus datos cuando exista. |
| Emisor de notificaciones | Validar una suscripción admitida y enviar un aviso al dispositivo asociado. |

Los adaptadores deberán transformar las respuestas externas
al formato esperado por Nivora y distinguir los errores
de comunicación de los resultados válidos.

Cambiar de proveedor requerirá implementar y verificar
el adaptador correspondiente. Conservar el contrato
no elimina la necesidad de adaptar formatos y condiciones.

## Validación del acceso

El acceso de consulta conservará los dos campos definidos:
código de suministro y DNI.

Antes de crear la sesión:

1. Se comprobará el formato de ambos campos.
2. Se verificará la existencia del suministro.
3. Se consultará la existencia del DNI y sus datos.
4. Se registrará el acceso exitoso y se creará la sesión.

Se aceptará cualquier persona que utilice un suministro
existente y un DNI válido, sin exigir titularidad.

Un resultado de datos inexistentes se distinguirá
de un fallo que impida completar la validación.

El registro conservará el DNI utilizado y la identidad
devuelta por la API. Esa consulta no demuestra
quién escribió físicamente el documento.

## Integración con la empresa de agua

El adaptador obtendrá los datos externos
sin incorporar reglas de cálculo propias de Nivora.

Las lecturas recibidas pasarán por las validaciones
y controles de duplicados definidos en el núcleo.

Las tarifas conservarán su identificación y vigencia.

Los recibos se tratarán como información proporcionada
por la empresa. Su importe definitivo no se sustituirá
por el costo estimado calculado en el panel.

La actualización y recuperación seguirán
lo establecido en ADR-003.

## Notificaciones al dispositivo

El backend conservará los avisos generados
y las asociaciones de dispositivos con suministros.

La entrega utilizará un adaptador externo
y se procesará sin depender de la interfaz abierta.

Para la interfaz web del boilerplate se utilizará Web Push,
con una suscripción del navegador y un service worker
encargado de recibir el evento y mostrar el aviso.

La recepción dependerá del permiso, la conectividad,
la compatibilidad y las condiciones de funcionamiento
del navegador y del dispositivo.

Los fallos de entrega conservarán el aviso interno
y permitirán reintentos controlados.

Una respuesta de aceptación del proveedor
no se considerará evidencia de recepción en pantalla.

La disponibilidad del recibo generará un aviso
con su periodo y fecha límite. En la demostración,
el plazo será de una semana desde su disponibilidad.

## Configuración y credenciales

Las credenciales de las API y las claves privadas
de notificación permanecerán en el backend.

La interfaz solo recibirá la información necesaria,
como la clave pública utilizada para suscribirse a Web Push.

La configuración local se conservará fuera
de los archivos registrados en Git.

Los controles que modifican escenarios simulados
se reservarán para el entorno de demostración
y su acceso administrativo.

## Justificación

Los contratos mantienen las necesidades de Nivora
separadas de los detalles particulares de los proveedores.

Los adaptadores permiten estudiar la integración simulada,
probar fallos y preparar la conexión real
sin introducir dependencias externas en las reglas del dominio.

Separar la entrega de notificaciones de las consultas
permite conservar los avisos cuando el servicio falla
y evita depender de que el usuario mantenga abierto el panel.

## Consecuencias

- Será necesario implementar y probar cada adaptador.
- Los errores externos deberán traducirse a resultados
  comprensibles para los casos de aplicación.
- Las integraciones reales requerirán revisar sus formatos,
  credenciales y condiciones de funcionamiento.
- El envío deberá conservar estados e identificación de avisos
  para gestionar reintentos y evitar eventos duplicados.
- Las suscripciones inválidas o expiradas deberán dejar
  de utilizarse para entregar notificaciones.
- La recepción con la aplicación cerrada deberá comprobarse
  en dispositivos compatibles.

## Alternativa considerada

Realizar llamadas a los proveedores directamente
desde la interfaz o desde las reglas de negocio
mezclaría sus detalles técnicos con las funciones de Nivora.

Esto dificultaría sustituir proveedores, probar fallos
y mantener protegidas las credenciales del backend.

Se adopta la integración mediante contratos y adaptadores.

## Relación con el boilerplate

`fuentes-http.js` implementa los contratos de agua
e identidad mediante las API HTTP locales simuladas.

`notificador-web-push.js` implementa el envío al dispositivo,
y `sw.js` contiene la recepción en el navegador.

La integración de identidad utiliza personas ficticias;
todavía no consulta una entidad oficial.

El adaptador Web Push está incluido, pero requiere
configurar sus claves y verificar la recepción
con la aplicación cerrada.

Las pruebas comprueban validaciones, fallos de las API,
conservación de avisos y reintentos con un emisor ficticio.
No acreditan recepción externa en un dispositivo físico.