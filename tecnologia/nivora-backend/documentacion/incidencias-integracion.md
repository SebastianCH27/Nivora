# Incidencias de integración por suministro

`GET /api/v1/supervision/suministros/{codigo}/incidencias` consulta los registros
locales de lecturas y recibos. Requiere una sesión administrativa con el permiso
`SUPERVISAR_INTEGRACION`. La consulta funciona con MySQL mientras Empresa de
Agua está caída.

| Origen | Qué registra | Estado |
| --- | --- | --- |
| `RECHAZO_LECTURA` | Una lectura rechazada durante la incorporación. Incluye `idLectura` y el motivo. | `POR_REVISAR` |
| `RECUPERACION_LECTURAS` | Fallos de fuente o ausencia del suministro durante la recuperación de lecturas. | `ABIERTA` o `RESUELTA` |
| `SINCRONIZACION_RECIBOS` | Fallos al consultar, validar o guardar recibos. | `ABIERTA` o `RESUELTA` |

Cada incidencia incluye la fuente, el motivo, la primera y última recepción en
UTC y el número total de repeticiones. Cuando aplica, `ultimaResolucion`
conserva la fecha del último cierre, incluso si un fallo posterior vuelve a
abrir la incidencia. `referencia` identifica la fila de rechazo o el motivo
de sincronización dentro de su origen y fuente. El contenido recibido y la
huella se conservan en MySQL para las lecturas rechazadas.

Los fallos de recibos se agrupan por suministro, fuente y motivo. Solo se
registran intentos confirmados con una reserva válida. Un ciclo exitoso cierra
los fallos abiertos de recibos aunque la fuente no tenga recibos nuevos.
El historial de recibos empieza con los intentos confirmados después de
aplicar el script y arrancar esta versión.

## Páginas y respuestas

La lista se ordena por primera recepción descendente. Para empates se usan
el origen, la fuente y la referencia. El cursor se mantiene estable si una
incidencia vuelve a ocurrir mientras se consulta el listado.

- `limite` acepta de 1 a 100 incidencias por página y vale 20 por defecto.
- Si `hayMas` es verdadero, envía `siguienteAntesDe` como parámetro `antesDe`,
  codificado como valor de URL.
- Un suministro registrado sin incidencias devuelve `200` con una lista vacía.
- `400` indica parámetros inválidos. `401` indica que hace falta una sesión
  administrativa válida. `403` indica que esa sesión carece del permiso.
  `404` indica que el suministro no está registrado. `503` indica un fallo local.
- Las respuestas incluyen `Cache-Control: no-store`.

## Preparar y comprobar en Windows

Aplica el script en el monitor `mysql>` después de los scripts de lecturas y
recibos. El historial de lecturas utiliza los registros incorporados previamente.

```sql
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/nivora-backend/base-datos/19-incidencias-sincronizacion-recibos.sql;
SHOW TABLES FROM nivora LIKE 'incidencias_sincronizacion_recibos';
```

Con el nuevo JAR de Nivora en el puerto `8082`, ejecuta esta línea en otro CMD.
Usa un suministro registrado. La clave se pide sin mostrarla y la sesión
administrativa se cierra al terminar.

```cmd
powershell -NoProfile -Command "& { $ErrorActionPreference = 'Stop'; $usuario = Read-Host 'Usuario administrativo'; $clave = Read-Host 'Clave administrativa' -AsSecureString; $codigo = Read-Host 'Codigo de suministro'; if ($codigo -notmatch '^[0-9]{1,20}$') { throw 'Escribe un codigo de suministro valido.' }; $body = @{usuario=$usuario; clave=[System.Net.NetworkCredential]::new('', $clave).Password} | ConvertTo-Json -Compress; $api = 'http://127.0.0.1:8082/api/v1'; $acceso = $api + '/acceso/administradores'; $sesion = Invoke-RestMethod -Method Post -Uri ($acceso + '/sesiones') -ContentType 'application/json' -Body $body -TimeoutSec 30; $h = @{Authorization='Bearer ' + $sesion.token}; try { $ruta = $api + '/supervision/suministros/' + $codigo + '/incidencias'; $r = Invoke-WebRequest -UseBasicParsing -Uri $ruta -Headers $h -TimeoutSec 30; Write-Host ('Incidencias HTTP ' + [int]$r.StatusCode); $r.Content } finally { Invoke-RestMethod -Method Delete -Uri ($acceso + '/sesion') -Headers $h -TimeoutSec 30 | Out-Null } }"
```
