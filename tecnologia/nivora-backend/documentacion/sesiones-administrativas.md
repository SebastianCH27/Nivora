# Inicio y cierre de sesion administrativa

Parte 2 del bloque 3. Utiliza las cuentas preparadas mediante el script 07.
Incorpora autenticacion HTTP, sesiones persistidas, comprobacion de permisos,
vencimientos, limites de intentos y cierre de la sesion actual.
Las altas adicionales y cambios de cuenta corresponden a la parte 3.

## Preparar e iniciar

Con Nivora detenido y los scripts 01-07 aplicados, ejecutar como root en
`mysql>`:

```sql
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/nivora-backend/base-datos/08-sesiones-administrativas.sql;
SHOW TABLES FROM nivora;
```

Agrega `sesiones_administrativas` y `limites_acceso_administrativo`, para un
total de 13 tablas. Es repetible y conserva cuentas, sesiones y datos previos.
La cuenta de ejecucion mantiene sus permisos DML sobre `nivora.*`.

Desde CMD, en `tecnologia\nivora-backend`, iniciar normalmente:

```bat
powershell -NoProfile -Command "$claveNivora = Read-Host 'Contrasena de nivora_app' -AsSecureString; $env:NIVORA_DB_CLAVE = [System.Net.NetworkCredential]::new('', $claveNivora).Password; & '.\mvnw.cmd' spring-boot:run"
```

No es necesario repetir la inicializacion de la cuenta ni habilitar la
excepcion de clave corta. El hash que ya existe se comprueba con la politica
normal, incluso si fue creado durante esa excepcion local.

## Contrato HTTP

| Metodo y ruta | Respuesta normal |
|---|---|
| `POST /api/v1/acceso/administradores/sesiones` | 201 con `tipoToken`, `token` y los metadatos de `sesion`. |
| `GET /api/v1/acceso/administradores/sesion` | 200 con metadatos de la sesion; no devuelve el token. |
| `DELETE /api/v1/acceso/administradores/sesion` | 204 al cerrar la sesion autenticada. |
| `PUT /api/v1/acceso/administradores/sesion/clave` | 200 al cambiar la clave propia; revoca las sesiones anteriores. |
| `GET /api/v1/supervision/suministros/{codigo}/lecturas` | 200, 400 o 404 de negocio cuando existe permiso de supervision. |
| `GET /actuator/health` | Comprobacion publica del servicio y su base. |

El inicio acepta exclusivamente un objeto JSON UTF-8 de hasta 4096 bytes,
con `usuario` y `clave`, ambos texto. Rechaza campos adicionales, claves JSON
duplicadas y contenido despues del objeto. El usuario conserva las reglas
del primer incremento; la clave se compara exactamente, sin recortarla.

| Situacion | Respuesta |
|---|---|
| Solicitud o JSON invalidos | 400. |
| Credenciales incorrectas, cuenta ausente o inactiva | 401 con el mismo mensaje generico. |
| Sesion ausente, invalida, caducada, cerrada o de version anterior | 401. |
| Sesion valida sin el permiso requerido | 403. |
| Cuerpo demasiado grande | 413. |
| Tipo de contenido distinto de JSON UTF-8 | 415. |
| Limite de intentos alcanzado | 429 y `Retry-After` en segundos. |
| Fallo de almacenamiento | 503; no se confunde con una clave incorrecta. |

Las respuestas de acceso y supervision usan `Cache-Control: no-store`.
Los 401 incluyen `WWW-Authenticate: Bearer`. No hay redirecciones a HTML,
formularios de login generados ni una cuenta `user` automatica.

## Token, vigencia y permisos

Cada inicio exitoso crea un token opaco de 32 bytes aleatorios, codificado
como 43 caracteres Base64URL sin relleno. Solo la respuesta inicial devuelve
su valor. MySQL guarda SHA-256 de ese token, junto con un identificador de
sesion independiente, usuario, version de credenciales y fechas UTC.
El hash de la clave administrativa sigue siendo Argon2id: son usos distintos.

El cliente envia el token exclusivamente en `Authorization: Bearer ...`.
No se aceptan cookies, parametros de URL ni autenticacion Basic. La API
utiliza Spring Security con contexto por solicitud, sin `HttpSession`.
CSRF esta desactivado para este contrato de cabecera explicita; incorporar
autenticacion mediante cookies requeriria modificar esa configuracion.
El entorno actual usa HTTP en `127.0.0.1`; un despliegue remoto debe usar HTTPS.

En cada solicitud protegida se vuelve a comprobar la cuenta y la sesion en
MySQL. Una cuenta inactiva o una version de credenciales diferente invalida
el acceso. Los permisos se obtienen de la cuenta actual, no de informacion
enviada por el cliente ni de una copia permanente dentro del token.

Todas estas sesiones tienen rol `ADMIN`. Una cuenta sin cambio de clave
pendiente recibe `SUPERVISAR_INTEGRACION`; el permiso
`GESTIONAR_ADMINISTRADORES` depende de su indicador guardado. Si existe cambio
de clave pendiente, solo puede consultar sus metadatos, cambiar su clave
y cerrar la sesion. Tras cambiarla, debe iniciar una nueva sesion.
Las altas, permisos, estados y claves se describen en
[Gestion de administradores](gestion-administradores.md), parte 3 del bloque.

| Propiedad bajo `nivora.sesiones-administrativas` | Valor inicial |
|---|---|
| `inactividad-segundos` | 900: quince minutos. |
| `duracion-maxima-segundos` | 28800: ocho horas. |
| `ventana-intentos-segundos` | 60. |
| `intentos-por-usuario` | 5. |
| `intentos-por-origen` | 30. |

La actividad valida renueva el limite de inactividad hasta el vencimiento
absoluto, que no se prolonga. Al alcanzar cualquiera de los dos limites, la
sesion deja de autenticar. El cierre revoca el token en MySQL; volver a
enviarlo no permite entrar. Las sesiones de otros dispositivos siguen
independientes. Reiniciar el backend conserva la vigencia y las revocaciones.
Una solicitud que ya habia sido autorizada puede terminar mientras se cierra
la sesion; el cierre impide reutilizar el token en solicitudes posteriores.

## Intentos y transacciones

Los limites cuentan intentos de inicio validamente formados, incluidos los
exitosos. Se aplican tanto al usuario canonico, incluso ausente, como al
origen de red observado por el servidor. No se confia en `X-Forwarded-For`
proporcionado por el cliente. Las ventanas y contadores se guardan en MySQL,
compartidos entre procesos. No se reinician por un login correcto ni por
reiniciar Nivora; al vencer la ventana se inicia otra. Los relojes de las
instancias deben ser coherentes. Esta limitacion no acredita resistencia a
ataques distribuidos ni las metas de carga del proyecto.

La verificacion Argon2 se realiza fuera de la transaccion de emision.
Un usuario ausente utiliza un hash de referencia con el mismo costo. Antes
de guardar la sesion se vuelven a comprobar cuenta, version y hash para
rechazar cambios concurrentes. La sesion y el evento `INICIO_SESION` se
confirman juntos. El cierre y su evento `CIERRE_SESION` tambien son atomicos;
si la escritura falla, se responde 503 y no se afirma un cierre exitoso.

Las operaciones bloquean primero la cuenta y despues la sesion. Actualizar
actividad nunca elimina una revocacion, incluso si coincide con un cierre.
Las filas caducadas o revocadas se conservan; una politica de limpieza y
retencion se evaluara junto con las pruebas de capacidad posteriores.

## Prueba desde CMD sin publicar claves ni tokens

Primero, en un CMD libre, comprobar que la supervision sin sesion responde 401:

```bat
curl.exe -i "http://127.0.0.1:8082/api/v1/supervision/suministros/13100390/lecturas"
```

El siguiente comando abre un cliente PowerShell temporal. Pide las
credenciales por consola, conserva el token en una variable y muestra solo
metadatos, supervision y el resultado del cierre. El token no se imprime.

```bat
powershell -NoProfile -Command "& { $ErrorActionPreference = 'Stop'; $usuario = Read-Host 'Usuario administrativo'; $clave = Read-Host 'Clave administrativa' -AsSecureString; $cuerpo = @{usuario=$usuario; clave=[System.Net.NetworkCredential]::new('', $clave).Password} | ConvertTo-Json -Compress; $inicio = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8082/api/v1/acceso/administradores/sesiones' -ContentType 'application/json' -Body ([System.Text.Encoding]::UTF8.GetBytes($cuerpo)); $cabeceras = @{Authorization='Bearer '+$inicio.token}; $inicio.sesion | ConvertTo-Json -Depth 5; Invoke-RestMethod -Uri 'http://127.0.0.1:8082/api/v1/supervision/suministros/13100390/lecturas' -Headers $cabeceras | ConvertTo-Json -Depth 10; Invoke-RestMethod -Method Delete -Uri 'http://127.0.0.1:8082/api/v1/acceso/administradores/sesion' -Headers $cabeceras; try { Invoke-RestMethod -Uri 'http://127.0.0.1:8082/api/v1/acceso/administradores/sesion' -Headers $cabeceras | Out-Null; throw 'La sesion cerrada sigue activa.' } catch { if ([int]$_.Exception.Response.StatusCode -ne 401) { throw }; Write-Host 'Cierre comprobado: HTTP 401 al reutilizar la sesion.' } }"
```

Debe mostrar el usuario y rol `ADMIN`, los permisos de la cuenta, el estado
guardado del suministro y la confirmacion HTTP 401 tras el cierre. La Empresa
puede estar apagada: estas operaciones consultan la base propia.

Para inspeccionar metadatos en `mysql>` sin seleccionar huellas ni tokens:

```sql
SELECT id_sesion, usuario, creada_en_utc, ultima_actividad_utc, expira_en_utc, inactiva_hasta_utc, revocada_en_utc FROM nivora.sesiones_administrativas ORDER BY creada_en_utc DESC LIMIT 5;
```

## Validacion

Las 31 pruebas nuevas utilizan HTTP real, H2 con los scripts 02-08 y un reloj
controlable. Cubren acceso correcto e incorrecto, cuentas ausentes e
inactivas, la clave corta creada previamente, tokens y transportes invalidos,
JSON acotado, cambios de version y permisos, expiracion, cierre, sesiones
independientes, limites por usuario y origen, fallos SQL, auditoria atomica
y concurrencia entre cierre y actividad. Las 20 pruebas de supervision se
conservan con autenticacion; el total de la suite es 315.

Tambien se comprobo con MySQL 8.0.46 y el servidor Spring real, usando una
cuenta de ejecucion con permisos DML: acceso con una clave corta ya
almacenada, huella del token, supervision protegida con la fuente apagada,
persistencia de sesion y limites tras reiniciar, cierre, caducidad y auditoria.
Reaplicar el script conserva cuentas, claves y lecturas existentes.

Referencias de diseno:

- [Spring Security y Spring Boot](https://docs.spring.io/spring-boot/reference/web/spring-security.html).
- [CSRF y aplicaciones sin estado](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html).
- [OWASP: gestion de sesiones](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet).
