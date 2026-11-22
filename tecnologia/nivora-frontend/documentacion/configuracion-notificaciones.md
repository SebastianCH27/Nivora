# Configuración de notificaciones por dispositivo y suministro

Base de esta entrega: `249be0eaea44c23bead4569ddd07060bec97351e` de `main`.

En **Notificaciones → Configurar notificaciones**, la app muestra el permiso
real del sistema y la preferencia confirmada por el backend para esta instalación
y el suministro de la sesión. La bandeja conserva su funcionamiento al desactivar
la preferencia. Esta entrega no implementa transporte push, avisos en segundo
plano, Firebase, tareas periódicas ni el control administrativo de alertas.

## Contrato revisado

Se revisaron la documentación, `ConfiguracionNotificacionesController`,
`ConfigurarNotificaciones` y `RepositorioConfiguracionNotificaciones` del backend.

| Operación | Ruta y resultado |
| --- | --- |
| Consultar | `GET /api/v1/acceso/consulta/notificaciones/dispositivos/{uuid}` → 200; 404 significa que aún no existe la preferencia |
| Guardar | `PUT` a la misma ruta → 200 con preferencia confirmada |
| Cuerpo PUT | `{"permiso":"CONCEDIDO","habilitadas":true}`; `habilitadas` es booleano JSON |
| Respuesta | `idDispositivo`, `permiso`, `habilitadas`, `actualizadaEn` UTC |

Todas las solicitudes llevan Bearer. El suministro procede de la sesión: no se
añaden DNI, suministro ni identificadores de hardware a estas peticiones. El UUID
usa el formato canónico en minúsculas. Se rechazan respuestas de otro UUID,
permisos desconocidos y activaciones sin permiso concedido. Un PUT debe devolver
el permiso y la activación solicitados antes de mostrarse como confirmado.

El backend debe tener aplicado su script existente
`base-datos/17-configuracion-notificaciones.sql`. No hay cambios de backend,
contrato, dependencias Flutter ni lockfile en este bloque.

## Comportamiento

- Después del ingreso y al desplegar la configuración se consulta el permiso y se
  sincroniza la preferencia del suministro actual. Al recuperar el foco se lee
  primero el permiso local sin cambiar la pantalla: solo se sincroniza con el
  servidor si el permiso cambió o la configuración quedó sin confirmar.
- La consulta inicial no muestra diálogos. Si el backend responde 404, se registra
  el permiso actual con `habilitadas: false`.
- **Solicitar permiso** es independiente del interruptor. Conceder permiso no
  activa una preferencia que estaba desactivada.
- Solo se permite activar con permiso `CONCEDIDO` y configuración confirmada.
- Revocar el permiso guarda `habilitadas: false`. Volver a concederlo conserva la
  desactivación hasta usar el interruptor. La sincronización requiere conexión y
  sesión; otros suministros se sincronizan cuando se inicia sesión con ellos.
- Si no hay cambios, no se envía PUT: se conserva `actualizadaEn` del servidor.
- Las operaciones duplicadas se bloquean. Un error de red no confirma cambios;
  se ofrece actualizar para conocer el estado real, incluso si se perdió una
  respuesta después de que el servidor guardó el cambio.
- Un 401 o el vencimiento limpia la sesión. Al salir o disponer el controlador se
  descartan resultados tardíos y no se inician nuevas escrituras de esa sesión.
  Una escritura ya enviada puede haber llegado al servidor; el siguiente ingreso
  consulta el estado guardado.

## Android

El canal nativo declara `POST_NOTIFICATIONS`, consulta `NotificationManager` y,
en Android 13 o superior, solicita el permiso al pulsar el botón. Distingue la
falta de decisión del usuario de una denegación previa con la API pública
`shouldShowRequestPermissionRationale` y un registro local de decisiones observadas;
cancelar/deslizar el diálogo no se transforma en permiso concedido. Si no hay una
decisión observable, se conserva `PENDIENTE`, también ante ciertos bloqueos por
políticas del sistema; ese estado nunca permite activar la preferencia. Si el sistema
ya no ofrece el diálogo o las notificaciones están bloqueadas, se usa **Abrir
ajustes del sistema**.

El UUID aleatorio se conserva en `noBackupFilesDir`: sobrevive a reinicios y
actualizaciones instaladas encima de la misma app, y no se copia a otro teléfono
mediante restauración. Desinstalar o borrar los datos crea una nueva identidad.
No se usa Android ID, IMEI ni un token de sesión para identificar el dispositivo.
No se crean canales ni notificaciones de prueba en esta entrega.

Referencias: [permiso de notificaciones de Android](https://developer.android.com/develop/ui/views/notifications/notification-permission).

## Windows

El UUID se conserva por usuario de Windows en
`HKCU\Software\Nivora\Consulta\IdInstalacion`, independientemente de la carpeta
de compilación. Reiniciar, recompilar o cambiar de suministro conserva el UUID.
Eliminar esa clave reinicia la identidad; cambiar de cuenta Windows crea otra.
Las ejecuciones con el mismo usuario comparten la identidad de Nivora.

Hasta preparar la identidad de notificaciones, se informa `PENDIENTE`. Al pulsar
**Solicitar permiso**, se crea/actualiza el acceso directo **Nivora - Consulta** en
el menú Inicio del usuario, con el AppUserModelID `pe.nivora.Consulta`, y se consulta
`ToastNotifier.Setting`. Windows no muestra el diálogo de Android: el permiso es
`CONCEDIDO` si el sistema permite las notificaciones y `DENEGADO` si las bloquea.
La operación no habilita notificaciones bloqueadas por el usuario o una política.
**Abrir ajustes del sistema** abre la configuración de notificaciones de Windows.

Este registro prepara la consulta de permiso; no crea un receptor push. Si se
elimina o mueve el ejecutable, volver a pulsar **Solicitar permiso** actualiza el
acceso directo. Para retirar esta preparación manualmente, se puede eliminar el
acceso directo del menú Inicio; la preferencia puede desactivarse antes en la app.

Referencias: [identidad y acceso directo de escritorio](https://learn.microsoft.com/en-us/windows/win32/shell/enable-desktop-toast-with-appusermodelid)
y [estado de ToastNotifier](https://learn.microsoft.com/en-us/uwp/api/windows.ui.notifications.toastnotifier.setting).

## Comprobación local en PC y Galaxy S22

1. Confirmar `git rev-parse --short HEAD`: debe ser `249be0e` antes de aplicar.
   Revisar `git status --short` y proteger cualquier cambio local propio.
2. Copiar los archivos de la entrega respetando la carpeta `tecnologia`.
3. En `tecnologia/nivora-frontend`, ejecutar:

   ```bat
   flutter pub get --enforce-lockfile
   flutter analyze
   flutter test
   flutter run -d windows --dart-define=NIVORA_API_URL=http://127.0.0.1:8082
   ```

   El modo debug permite el HTTP local, como en los bloques anteriores.
4. Abrir **Notificaciones → Configurar notificaciones**. Comprobar permiso y estado
   guardado, solicitar permiso y activar. Desactivar y verificar la confirmación.
5. Cerrar la app y abrirla de nuevo: al ingresar con el mismo suministro se conserva
   la preferencia. Entrar con otro suministro deja su preferencia independiente.
6. En Windows, comprobar el acceso directo en Inicio después de solicitar permiso.
   Si Windows bloquea las notificaciones, abrir sus ajustes, cambiar el bloqueo,
   volver a la app y actualizar. El interruptor no se activa por conceder permiso.
7. Compilar APK con `flutter build apk --debug`, usando los parámetros del README.
   En la laptop de 4 GB, detener los servicios Java durante la compilación y
   reiniciarlos antes de probar. Instalar encima con `adb install -r` para conservar
   el UUID y los datos. Configurar la conexión ADB al backend según el README.
8. En el S22, comprobar el diálogo al solicitar, la activación y su persistencia
   al cerrar/abrir. Denegar el permiso o revocarlo desde Ajustes del teléfono y
   volver a la app: debe sincronizar desactivadas. Concederlo otra vez no reactiva.
9. Activar en un dispositivo y desactivar en el otro con el mismo suministro:
   cada uno conserva su preferencia. La marca de lectura de la bandeja sigue
   compartida por suministro, como antes.
10. Probar sin conexión y recuperar con **Actualizar configuración**. Durante una
    solicitud se puede cerrar sesión; no deben aparecer datos de la sesión anterior.

Las pruebas Dart cubren modelos, HTTP, controlador, canal de plataforma simulado y
widgets, incluidos anchos de 320 y 1200 px con texto ampliado. La compilación nativa
de Kotlin/C++ y la comprobación con MySQL, Windows y el S22 se realizan en el PC del
usuario; los mocks del canal no validan esas APIs nativas.

## Validación de la primera entrega (498 pruebas)

Con Flutter 3.50.1 y Dart 3.14.0 en Linux:

- `flutter pub get --enforce-lockfile`: correcto, sin cambios de dependencias.
- `flutter analyze --no-pub`: sin incidencias.
- `flutter test --no-pub`: 498 pruebas correctas (451 existentes y 47 nuevas).
- `git diff --check`: correcto.

La compilación nativa Android/Windows y las pruebas con los servicios del PC
siguen pendientes de la comprobación local indicada arriba.

## Revisión del adaptador de Windows

La primera prueba local permitió compilar la app, pero la consulta del dispositivo
mostró un error genérico al solicitar permiso. Las capturas no contienen el
HRESULT original, por lo que todavía no identifican cuál llamada de Windows falló.

Esta revisión corrige el orden de preparación: inicia Windows Runtime/COM antes
de crear o leer el acceso directo y conserva el modelo STA/MTA existente. Equilibra
las inicializaciones correctas con su cierre, incluido el caso `S_FALSE`. Además,
establece la identidad del proceso antes de crear la ventana, con el mismo
AppUserModelID del acceso directo. Al consultar se comprueba la identidad del
acceso, no solo que exista el archivo. Se avisa al shell cuando se actualiza.

Los fallos de Windows conservan una referencia de etapa y HRESULT tanto en el
panel como en la consola de `flutter run`, por ejemplo
`CREAR_ACCESO/0x800401F0`. Esa referencia no contiene el token, el DNI, la ruta del
usuario ni el UUID. Un fallo nunca se convierte en permiso concedido. El panel
también explica que Windows no muestra el diálogo de Android, y solicitar permiso
presenta mensajes distintos para concedido, denegado y pendiente.

La modificación requiere cerrar y volver a compilar la app. Hot reload o hot
restart no actualizan el código C++ del ejecutable. La prueba real en Windows sigue
pendiente: la corrección del orden y la información de diagnóstico no demuestran
por sí solas que se haya resuelto el fallo observado en la laptop.

Referencias de las APIs utilizadas:

- [Inicialización de Windows Runtime](https://learn.microsoft.com/en-us/windows/win32/api/roapi/nf-roapi-roinitialize).
- [Identidad del proceso antes de presentar la interfaz](https://learn.microsoft.com/en-us/windows/win32/api/shobjidl_core/nf-shobjidl_core-setcurrentprocessexplicitappusermodelid).

### Validación de la revisión de Windows

Con Flutter 3.50.1 y Dart 3.14.0 en Linux:

- `flutter pub get --enforce-lockfile`: correcto, sin modificar el lockfile.
- `flutter analyze --no-pub`: sin incidencias.
- `flutter test --no-pub`: 507 pruebas correctas (498 anteriores y 9 adicionales).
- `git diff --check`: correcto.

Las pruebas adicionales verifican la transmisión de referencias de error, los
mensajes concretos de permiso y el comportamiento del panel con Windows como
plataforma. No ejecutan COM, Shell ni el API de notificaciones de Windows. La
compilación C++ y la resolución del problema observado requieren validación en la
laptop del usuario.

## Registro del emisor y primer estado en Windows

La prueba en la laptop identificó la referencia `CONSULTAR_PERMISO/0x80070490` y
confirmó que faltaba la clave
`HKCU\Software\Classes\AppUserModelId\pe.nivora.Consulta`. Esta revisión añade
su `DisplayName` al pulsar **Solicitar permiso**, además del acceso directo que ya
se preparaba. Se conserva el UUID de instalación y no se escriben claves de
activación de notificaciones, bloqueos ni políticas de Windows.

Las consultas automáticas solo leen esa identidad. Si falta, o si `Setting`
devuelve exactamente `ERROR_NOT_FOUND`, el resultado es `PENDIENTE`; otros fallos
conservan su referencia. Ninguno se interpreta como `CONCEDIDO`.

Si el usuario pulsa **Solicitar permiso** y el sistema todavía no tiene un estado,
la app puede mostrar un único aviso de prueba en esa operación y consultar otra
vez. El aviso dice explícitamente que es una prueba, no corresponde a consumo ni
recibos, no lleva sonido y expira al minuto. Usa un tag y grupo fijos para sustituir
la misma prueba al repetir la acción. No se muestra al ingresar, al actualizar ni
al reanudar. Si el permiso ya es conocido, incluido `DENEGADO`, no se muestra.
Llamar a `Show` no se considera prueba de entrega ni concesión: solo `Setting`
con `Enabled` produce `CONCEDIDO`. Si el estado sigue sin existir, permanece
pendiente y se puede pulsar **Actualizar configuración** después.

Esta prueba local de preparación no implementa la recepción push con la app
cerrada. La activación por suministro sigue requiriendo el interruptor y la
confirmación del backend.

Después de copiar esta revisión y volver a compilar, pulsar **Solicitar permiso**.
Comprobar después la entrada con:

```bat
reg query "HKCU\Software\Classes\AppUserModelId\pe.nivora.Consulta"
```

Debe existir `DisplayName` con valor `Nivora`. El estado real del permiso se lee en
el panel; que exista la clave no concede permiso por sí solo.

La secuencia de consulta y aviso se implementa en un módulo C++ portable compartido
por el adaptador. Sus pruebas verifican que una consulta no muestre avisos, que una
denegación se respete, que solicitar con estado pendiente consulte de nuevo y que
los fallos se propaguen. Estas pruebas no ejecutan las APIs de Windows.

Fuentes primarias para el registro y la consulta:

- [Registro de metadatos del emisor en Windows App SDK](https://github.com/microsoft/WindowsAppSDK/blob/main/dev/AppNotifications/AppNotificationUtility.cpp).
- [Consulta del permiso y diagnóstico de aplicaciones sin registro completo](https://devblogs.microsoft.com/oldnewthing/20200810-00/?p=104058).

### Validación del registro del emisor

En Linux, con Flutter 3.50.1 y Dart 3.14.0:

- Dependencias resueltas con `--enforce-lockfile`, sin cambiar el lockfile.
- `flutter analyze --no-pub`: sin incidencias.
- `flutter test --no-pub`: 507 pruebas correctas.
- Módulo C++ portable: 11 comprobaciones correctas, compiladas con
  `g++ -std=c++17 -Wall -Wextra -Werror -pedantic`.
- `git diff --check`: correcto.

Las pruebas C++ ejecutan la política compartida con el adaptador, pero no las
funciones del Registro, Shell ni Windows Runtime. La compilación del adaptador
completo y su ejecución con Windows 10 deben comprobarse en el PC del usuario.

## Primera revisión de la presentación durante la comprobación

Al abrir el panel o volver a la aplicación se mantiene la consulta automática.
Durante esa consulta se bloquea la edición, pero el interruptor conserva el último
estado confirmado mientras el permiso conocido siga concedido. Antes se dibujaba
apagado por usar la confirmación de la consulta en curso como parte de su valor.
Ese cambio visual no correspondía a una desactivación guardada.

Mientras se comprueba, la etiqueta es **Último estado confirmado** y se conserva
la fecha conocida. Al recibir la respuesta correcta vuelve **Estado guardado**.
Si falla, se mantiene el último dato claramente identificado y la edición sigue
bloqueada hasta recuperar la confirmación. No se confirma un cambio del usuario
antes de la respuesta del servidor. Si se detecta permiso denegado, el control se
apaga y permanece bloqueado aunque quede un estado anterior guardado como activo.
Cerrar sesión elimina los datos anteriores, incluso durante una consulta.

Esta revisión cambia solo la presentación y sus pruebas. Conserva las consultas,
la comprobación al regresar, el contrato HTTP, los adaptadores nativos y el UUID.
No añade escrituras al backend ni cambia la fecha del servidor al refrescar.

### Validación de la presentación durante la carga

- Se reprodujo el fallo visual en las pruebas: el estado activo se dibujaba falso
  mientras la consulta seguía pendiente.
- Cuatro pruebas adicionales comprueban actualización pendiente, fallo de red,
  revocación y salida durante una consulta.
- `flutter analyze --no-pub`: sin incidencias.
- `flutter test --no-pub`: 511 pruebas correctas.
- `git diff --check`: correcto.

Validación ejecutada con Flutter 3.50.1 y Dart 3.14.0 en Linux. Queda comprobar
visualmente el ajuste en la aplicación Windows del usuario y continuar las pruebas
del permiso y de Android que ya estaban pendientes.

## Evitar la recarga al cambiar de ventana

El video de la prueba en Windows mostró que conservar el valor del interruptor
no resolvía el salto del contenido. Cada evento de reanudación consultaba de nuevo
el backend, añadía el indicador de carga y deshabilitaba los controles.

Ahora, al volver a la ventana, se consulta únicamente el permiso local. Si coincide
con el conocido y la configuración está confirmada, no se recarga el backend, no
se muestra el indicador, no se bloquean los controles y no cambia la altura del
panel. **Actualizar configuración**, abrir el panel y entrar con un suministro
siguen consultando el estado del servidor.

Si cambia el permiso, se ejecuta la sincronización completa: revocar desactiva y
volver a conceder no reactiva por sí solo. Si una sincronización anterior falló,
se intenta recuperar la confirmación. Un error al leer el permiso conserva el
último dato como no confirmado y bloquea la edición.

Los regresos repetidos durante una lectura se agrupan. Una acción manual o una
salida de sesión invalida las lecturas anteriores para evitar que una respuesta
tardía sustituya el estado actual. Durante una escritura se comprueba el permiso
de nuevo al terminar si hubo un regreso pendiente.

### Validación del cambio de ventana

- Se reprodujo con una prueba el bloqueo innecesario al recuperar el foco.
- Se comprueban tres ciclos de perder/recuperar foco en la app completa: sin
  consultas HTTP adicionales, sin controles grises y sin desplazamiento del panel.
- Se comprueban revocación, recuperación tras error, regresos rápidos, cambios
  manuales durante una lectura y respuestas tardías tras salir, vencer o disponer.
- `flutter analyze --no-pub`: sin incidencias.
- `flutter test --no-pub`: 521 pruebas correctas.
- `git diff --check`: correcto.

Validación con Flutter 3.50.1 y Dart 3.14.0 en Linux. El comportamiento visual debe
comprobarse en Windows reproduciendo el cambio de ventanas del video.
