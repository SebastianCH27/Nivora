# Sesiones de consulta con suministro y DNI

Parte 5 del bloque de acceso. Nivora valida el formato de ambos campos, verifica
el suministro con la Empresa de Agua y consulta el DNI mediante APIsPERU. La
persona asociada al DNI puede ser distinta del titular del suministro.

Con ambas consultas confirmadas, una transaccion conserva el suministro y
crea la fila de `sesiones_consulta`. Esa fila registra el acceso exitoso con
el suministro, DNI utilizado, nombre devuelto y fecha UTC. El cierre y la
caducidad conservan el registro del acceso.

## Contrato HTTP

| Metodo y ruta | Respuesta |
|---|---|
| `POST /api/v1/acceso/consulta/sesiones` | HTTP 201 con `tipoToken`, `token` y `sesion`. |
| `GET /api/v1/acceso/consulta/sesion` | HTTP 200 con la sesion autenticada. |
| `DELETE /api/v1/acceso/consulta/sesion` | HTTP 204 al cerrar la sesion actual. |

El inicio recibe exclusivamente los campos de texto `codigoSuministro` y
`dni` en un objeto JSON UTF-8 de hasta 4096 bytes. Ambos conservan sus
ceros iniciales. La respuesta `sesion` incluye `idSesion`,
`codigoSuministro`, `nombre`, `rol` con valor `CONSULTA`, `creadaEn` y
`expiraEn`. El cliente conserva el token de la respuesta inicial y lo
envia en `Authorization: Bearer ...` para leer o cerrar su sesion.

| Situacion | Respuesta |
|---|---|
| Campos invalidos o JSON incompatible | HTTP 400. |
| Cuerpo demasiado grande o formato HTTP incompatible | HTTP 413 o 415. |
| Suministro ausente confirmado | HTTP 401 con un mensaje de acceso no valido. |
| Sesion ausente, invalida, caducada o cerrada | HTTP 401 con `WWW-Authenticate: Bearer`. |
| Limite local de intentos alcanzado | HTTP 429 con `Retry-After`. |
| Fallo de una fuente, DNI sin confirmar o fallo de almacenamiento | HTTP 503. |

MySQL conserva la huella SHA-256 del token y el suministro autorizado.
Cada solicitud protegida consulta el estado de la sesion persistida. La
credencial de consulta corresponde a las rutas de consulta. Los permisos
administrativos siguen sujetos a la autenticacion administrativa. Las
respuestas de estas rutas llevan `Cache-Control: no-store`. El backend de
desarrollo escucha en `127.0.0.1`. Un despliegue remoto debe usar HTTPS.

La vigencia es de **30 minutos** desde la creacion. El limite inicial es de
**30 intentos por origen en 60 segundos**. Se consume un intento por
solicitud con formato valido antes de consultar a las fuentes. Los valores
se configuran en `nivora.sesiones-consulta` en `application.properties`.

## Preparar MySQL

Con los scripts 01 a 08 aplicados, abre el cliente MySQL desde CMD.

```bat
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p
```

En `mysql>`, ejecuta la migracion y comprueba las dos tablas.

```sql
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/nivora-backend/base-datos/09-sesiones-consulta.sql;
SHOW TABLES FROM nivora LIKE 'sesiones_consulta';
SHOW TABLES FROM nivora LIKE 'limites_acceso_consulta';
EXIT;
```

El script es repetible y conserva los registros existentes. La cuenta
`nivora_app` utiliza los permisos DML sobre `nivora.*` ya asignados.

## Probar desde Windows

Compila Nivora en el CMD de `tecnologia\nivora-backend`.

```bat
mvnw.cmd clean verify
```

En otro CMD, inicia la Empresa de Agua en el puerto 8081. Escribe la
contrasena de `empresa_app` en el campo oculto.

```bat
cd /d C:\Users\HP\Desktop\Nivora\tecnologia\empresa-agua
powershell -NoProfile -Command "$clave = Read-Host 'Contrasena de empresa_app' -AsSecureString; $env:EMPRESA_DB_CLAVE = [System.Net.NetworkCredential]::new('', $clave).Password; & '.\mvnw.cmd' spring-boot:run"
```

En el CMD de Nivora, inicia el JAR compilado en el puerto 8082. Tras ingresar
la contrasena de `nivora_app`, pulsa **Copiar** junto a **Mi Token** en el
panel de APIsPERU y vuelve a CMD para presionar Enter.

```bat
powershell -NoProfile -STA -Command "& { $clave = Read-Host 'Contrasena de nivora_app' -AsSecureString; $env:NIVORA_DB_CLAVE = [System.Net.NetworkCredential]::new('', $clave).Password; $null = Read-Host 'Pulsa Copiar junto a Mi Token de APIsPERU y vuelve aqui para presionar Enter' -AsSecureString; $env:NIVORA_IDENTIDAD_API_TOKEN = ([string](Get-Clipboard -Raw)).Trim(); java -jar '.\target\nivora-backend-0.0.1-SNAPSHOT.jar' }"
```

Mantén abiertas ambas consolas de servidor. En un tercer CMD, ejecuta la
prueba con un suministro cargado en la Empresa de Agua, como `13100390`,
y tu propio DNI. La prueba conserva el token de sesion en memoria e imprime
los codigos HTTP.

```bat
powershell -NoProfile -Command "& { $codigo = Read-Host 'Codigo de suministro'; $seguro = Read-Host 'DNI de 8 digitos' -AsSecureString; $dni = [System.Net.NetworkCredential]::new('', $seguro).Password; $base = 'http://127.0.0.1:8082/api/v1/acceso/consulta'; $body = @{codigoSuministro=$codigo; dni=$dni} | ConvertTo-Json -Compress; try { $inicio = Invoke-WebRequest -UseBasicParsing -Method Post -Uri ($base + '/sesiones') -ContentType 'application/json' -Body $body -TimeoutSec 30 -ErrorAction Stop; Write-Host ('Inicio HTTP ' + [int]$inicio.StatusCode); $token = ($inicio.Content | ConvertFrom-Json).token; $cabecera = @{Authorization=('Bearer ' + $token)}; $sesion = Invoke-WebRequest -UseBasicParsing -Uri ($base + '/sesion') -Headers $cabecera -ErrorAction Stop; Write-Host ('Sesion HTTP ' + [int]$sesion.StatusCode); $cierre = Invoke-WebRequest -UseBasicParsing -Method Delete -Uri ($base + '/sesion') -Headers $cabecera -ErrorAction Stop; Write-Host ('Cierre HTTP ' + [int]$cierre.StatusCode); $null = Invoke-WebRequest -UseBasicParsing -Uri ($base + '/sesion') -Headers $cabecera -ErrorAction Stop; Write-Host 'La sesion cerrada aun respondio como valida' } catch { if ($_.Exception.Response) { Write-Host ('Respuesta HTTP ' + [int]$_.Exception.Response.StatusCode) } else { Write-Host ('Fallo de conexion ' + $_.Exception.GetType().Name) } } }"
```

La secuencia esperada es `Inicio HTTP 201`, `Sesion HTTP 200`,
`Cierre HTTP 204` y `Respuesta HTTP 401` al intentar reutilizar la sesion cerrada.
