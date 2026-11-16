# Entrega de notificaciones con Web Push

Nivora puede enviar alertas de consumo y avisos de recibo a navegadores
que concedan el permiso. El aviso de recibo incluye el periodo y la fecha
de vencimiento. La suscripción se registra con la sesión de consulta y
queda asociada al suministro de esa sesión y al UUID del dispositivo.
Cada endpoint se asocia a un solo suministro y dispositivo.

Se sirven la página local `/web-push.html` y su service worker desde el
backend. Abre la página mediante localhost en Windows o HTTPS en un
despliegue. El navegador conserva el UUID y el suministro asociado.
El token de consulta vive en memoria durante el alta o la baja.

## Preparación en Windows

Compila con `mvnw.cmd clean verify`. Aplica
`base-datos/18-entregas-web-push.sql` desde `mysql>` después del script 17.
Comprueba las tablas `suscripciones_web_push` y `entregas_web_push`.

Desde CMD en `tecnologia\nivora-backend`, genera un par VAPID una sola vez.

```cmd
java herramientas\GenerarClavesVapid.java "%USERPROFILE%\nivora-vapid.json"
```

El archivo queda fuera del repositorio. El comando conserva un archivo
existente. Guarda ese par para usarlo en los siguientes arranques.
Si cambias de par, vuelve a activar los avisos en el navegador.

En un CMD de `nivora-backend`, inicia Nivora. Mantén Empresa de Agua
corriendo en 8081. Cuando se solicite el token, cópialo desde APIsPERU
y pulsa Enter. Escribe un correo de contacto para VAPID.

```cmd
powershell -NoProfile -STA -Command "& { $ErrorActionPreference = 'Stop'; $vapid = Get-Content -LiteralPath (Join-Path $env:USERPROFILE 'nivora-vapid.json') -Raw | ConvertFrom-Json; $env:NIVORA_WEB_PUSH_PUBLICA = $vapid.publica; $env:NIVORA_WEB_PUSH_PRIVADA = $vapid.privada; $env:NIVORA_WEB_PUSH_CONTACTO = 'mailto:' + (Read-Host 'Correo de contacto VAPID'); $env:NIVORA_WEB_PUSH_HABILITADO = 'true'; $clave = Read-Host 'Contrasena de nivora_app' -AsSecureString; $env:NIVORA_DB_CLAVE = [System.Net.NetworkCredential]::new('', $clave).Password; $null = Read-Host 'Copia Mi Token de APIsPERU y presiona Enter'; $env:NIVORA_IDENTIDAD_API_TOKEN = ([string](Get-Clipboard -Raw)).Trim(); java -jar '.\target\nivora-backend-0.0.1-SNAPSHOT.jar' }"
```

1. Abre [la página de notificaciones](http://127.0.0.1:8082/web-push.html)
   en un perfil de navegador compatible con Web Push.
2. Ingresa el suministro y tu DNI. Presiona **Activar avisos** y concede
   el permiso cuando el navegador lo solicite. Pulsa **Confirmar activación**.
3. Confirma que aparece **Avisos activados para este navegador** y que
   MySQL muestra la suscripción. La consulta omite el endpoint y sus claves.

```sql
SELECT codigo_suministro, id_dispositivo, registrada_en_utc, expirada_en_utc FROM nivora.suscripciones_web_push;
```

La opción **Desactivar avisos** da de baja la suscripción y detiene sus
entregas pendientes. Deja el dispositivo activo para verificar la
recepción de un aviso nuevo en la parte 6. Los avisos previos siguen
disponibles en la bandeja.

## Contrato y cola

Las rutas están bajo `/api/v1/acceso/consulta/notificaciones` y requieren
el token de consulta.

| Método y ruta | Resultado |
|---|---|
| `GET /web-push/clave-publica` | HTTP 200 con `clavePublica`. |
| `PUT /dispositivos/{id}/suscripcion` | HTTP 204. Recibe `endpoint` y `keys` con `p256dh` y `auth`. La preferencia debe estar activa y el permiso concedido. |
| `DELETE /dispositivos/{id}/suscripcion` | HTTP 204. Elimina la suscripción del suministro de la sesión. |

El alta rechaza datos inválidos con HTTP 400 y una preferencia inactiva o
un endpoint ya asociado con HTTP 409. El alta y la clave pública
devuelven HTTP 503 mientras Web Push esté deshabilitado. Las URLs deben
corresponder a las rutas de Web Push admitidas para Google, Mozilla o
Apple. El cliente HTTP envía por HTTPS y conserva el destino original
si el proveedor devuelve una redirección, que cuenta como rechazo.

La cola registra una entrega por aviso y suscripción activa. Solo
encola avisos registrados desde que se guardó la suscripción y se
habilitó la preferencia. La reserva dura 60 segundos. Un fallo temporal
se reintenta desde 30 segundos con espera exponencial y hasta seis
intentos. La espera respeta `Retry-After` hasta un día. Un 404 o 410
invalida la suscripción. La baja elimina las filas de su cola. La
preferencia se comprueba antes de enviar. Los envíos que ya salieron
hacia el proveedor pueden llegar después de desactivar el dispositivo.

`ACEPTADA` significa que el proveedor respondió con 2xx. La
comprobación visual en el navegador se hace por separado. Un reinicio
durante el envío puede provocar un reintento. El mensaje conserva el
mismo `Topic` y la notificación usa el mismo identificador como
`tag` para reducir duplicados visibles.
