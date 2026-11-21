# Bandeja de notificaciones

La pestaña **Notificaciones** consulta los avisos guardados para el suministro de
la sesión. Presenta alertas de consumo y avisos de recibos disponibles, ordenados
del más reciente al más antiguo. Abrir el detalle no cambia el estado de lectura.

## Uso

- **Todos los avisos** incluye pendientes y leídos. **Solo pendientes** consulta
  el filtro del servidor; no se limita a ocultar elementos de la página actual.
- **Actualizar notificaciones** vuelve a los avisos más recientes del filtro.
- **Cargar más avisos** añade hasta 20 resultados usando el cursor del servidor.
  El contador indica los avisos mostrados, no el total de la bandeja.
- Cada tarjeta se despliega para ver el detalle. Las alertas muestran los valores
  del mes indicado y del anterior al detectarse la alerta; no son una consulta
  del consumo actual. Los avisos de recibo muestran número, disponibilidad y
  vencimiento. No incluyen un importe porque este contrato no lo devuelve.
- **Marcar como leída** cambia el estado únicamente tras recibir HTTP 204. En
  **Solo pendientes**, el aviso confirmado desaparece y se conserva la posibilidad
  de cargar avisos anteriores. Leer un aviso no paga un recibo.
- La marca se guarda por suministro, no por DNI ni dispositivo. Se comparte entre
  quienes consultan el mismo suministro y se conserva entre sesiones.
- El PUT no devuelve la fecha de lectura. El estado se actualiza tras confirmarse;
  **Actualizar notificaciones** recupera la fecha del servidor. No se inventa una
  fecha con el reloj del teléfono ni se altera la primera lectura existente.

## Contrato utilizado

| Operación | Solicitud |
| --- | --- |
| Consultar | `GET /api/v1/acceso/consulta/notificaciones?limite=20&soloPendientes=false` |
| Filtrar | El mismo GET con `soloPendientes=true` |
| Cargar más | El mismo filtro y `antesDe` con el `siguienteAntesDe` recibido |
| Marcar | `PUT /api/v1/acceso/consulta/notificaciones/{id}/leida`, sin cuerpo, HTTP 204 |

Todas las peticiones usan el Bearer de la sesión. El suministro se toma en el
backend; no se envía el DNI ni un código de suministro en estas rutas.

Se valida la identidad del suministro, los tipos `ALERTA_CONSUMO` y
`RECIBO_DISPONIBLE`, el identificador y el periodo, las fechas, el estado de
lectura y el contenido correspondiente al tipo. La paginación valida el orden
descendente por fecha, tipo y periodo, los empates con microsegundos, el filtro y
el límite. El cursor se conserva sin reconstruirlo y se codifica en la URL.
Los litros y su conversión a m³ usan enteros exactos, sin `double`.

## Fallos y sesión

Si falla una actualización, se conservan los datos del mismo filtro y se avisa que
corresponden a la consulta anterior. Cambiar de filtro retira los datos previos.
Si falla cargar más, se conserva la lista y el cursor para reintentar esa página.
Un error al marcar conserva el estado pendiente. Si se pierde la respuesta después
de que el servidor guardó la marca, actualizar permite conocer su estado real;
repetir el PUT es idempotente en el backend.

Un 404 al marcar informa que el aviso ya no está disponible. Un 401 o el vencimiento
local elimina la sesión y todas sus consultas. Las respuestas tardías se descartan
al salir, vencer, disponer el controlador o comenzar otra sesión. No se permiten
marcas duplicadas ni cambios de filtro durante una carga o marca. Salir sigue
disponible mientras una petición está en curso.

La bandeja usa las tablas existentes de los scripts 14, 15 y 16 del backend. Esta
entrega no modifica el backend, las dependencias ni los scripts de base de datos.
Si esos scripts todavía no se aplicaron en un entorno, debe completarse su
preparación según `nivora-backend/documentacion/bandeja-notificaciones.md`.

## Comprobación en Windows y S22

1. Ejecutar `flutter pub get --enforce-lockfile`, `flutter analyze` y `flutter test`.
2. Compilar con `flutter build apk --debug`. En la laptop de 4 GB, hacerlo con los
   servicios Java detenidos. Luego iniciarlos y usar los comandos ADB del README.
3. Ingresar y abrir **Notificaciones**. Comprobar los avisos reales o el mensaje
   vacío del suministro. No se generan avisos ficticios en la app.
4. Si hay un aviso, desplegarlo y revisar el periodo, las fechas y el tipo. Abrir
   y cerrar el detalle debe conservar su estado pendiente.
5. Seleccionar **Solo pendientes**, desplegar uno y pulsar **Marcar como leída**.
   Debe desaparecer tras confirmarse. Volver a **Todos los avisos**, actualizar
   y comprobar el estado leído y la fecha de lectura en el detalle.
6. Cerrar sesión e ingresar de nuevo con el mismo suministro. Actualizar la
   bandeja debe conservar la marca. Cambiar de DNI no reinicia la lectura.
7. Si hay más de 20 resultados, cargar más y comprobar que los anteriores se
   mantienen. Actualizar vuelve a los más recientes con el mismo filtro.
8. Ante una desconexión, comprobar el mensaje y la posibilidad de reintentar.
   Una marca sin confirmar debe permitir actualizar para conocer el estado real.

Las pruebas automatizadas cubren modelos, peticiones HTTP locales, paginación,
filtros, estados vacíos, lecturas confirmadas y fallidas, cierre/vencimiento durante
peticiones y pantallas de 320 y 1200 px con letra ampliada. La comprobación con
MySQL y el S22 corresponde al entorno local del usuario.

Esta es la bandeja dentro de la aplicación. Las notificaciones del sistema de
Android y su recepción con la app cerrada requieren una entrega independiente.
