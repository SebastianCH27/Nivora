# Reintentos de integración por suministro

Estas solicitudes requieren una sesión administrativa con el permiso
`REINTENTAR_INTEGRACION`. Se envían sin cuerpo.

| Solicitud | Operación |
| --- | --- |
| `POST /api/v1/supervision/suministros/{codigo}/lecturas/reintentos` | Adelanta el siguiente ciclo de recuperación de lecturas. |
| `POST /api/v1/supervision/suministros/{codigo}/recibos/reintentos` | Adelanta el siguiente ciclo de importación de recibos. |

`202 Accepted` devuelve `codigoSuministro`, `proceso` (`LECTURAS` o `RECIBOS`),
`estado: PROGRAMADO` y `proximoIntento` en UTC. La cabecera `Location` apunta a
la consulta de supervisión de ese proceso. El resultado de la sincronización
se observa allí después de que el trabajador procese el suministro.

Una solicitud mueve el próximo intento del calendario a la fecha actual.
Si ya estaba previsto para esa fecha o antes, se conserva ese turno.
Con la tarea automática deshabilitada, el turno queda guardado para cuando
vuelva a habilitarse. El trabajador usa su calendario habitual, reserva el
suministro y aplica los límites, validaciones y control de duplicados de
cada proceso. El último resultado y los fallos acumulados se conservan
hasta la confirmación del nuevo ciclo.

| HTTP | Causa |
| --- | --- |
| `400` | Código de suministro o proceso inválido. |
| `401` | Falta una sesión administrativa válida. |
| `403` | La sesión carece de permiso para solicitar reintentos. |
| `404` | El suministro no está registrado en Nivora. |
| `409` | Hay una reserva vigente para el mismo proceso o el recorrido de lecturas está bloqueado. |
| `503` | El calendario local no está disponible. |

Ante una reserva vigente, consulta la supervisión para ver el resultado
del intento en curso. Para un recorrido bloqueado, revisa la incidencia y
aplica el [reinicio del recorrido](recuperacion-lecturas.md) después de
corregir su causa.

## Comprobación en Windows

Arranca Nivora con los calendarios existentes de `06-sincronizacion-lecturas.sql`
y `13-sincronizacion-recibos.sql`, y el historial de recibos creado por
`19-incidencias-sincronizacion-recibos.sql`. Con el backend en `8082`, pega
esta línea en otro CMD. Pide la clave administrativa sin mostrarla y cierra
la sesión al terminar.

```cmd
powershell -NoProfile -Command "& { $ErrorActionPreference = 'Stop'; $usuario = Read-Host 'Usuario administrativo'; $clave = Read-Host 'Clave administrativa' -AsSecureString; $codigo = Read-Host 'Codigo de suministro'; if ($codigo -notmatch '^[0-9]{1,20}$') { throw 'Escribe un codigo de suministro valido.' }; $body = @{usuario=$usuario; clave=[System.Net.NetworkCredential]::new('', $clave).Password} | ConvertTo-Json -Compress; $api = 'http://127.0.0.1:8082/api/v1'; $acceso = $api + '/acceso/administradores'; $inicio = Invoke-RestMethod -Method Post -Uri ($acceso + '/sesiones') -ContentType 'application/json' -Body $body -TimeoutSec 30; $h = @{Authorization='Bearer ' + $inicio.token}; try { $ruta = $api + '/supervision/suministros/' + $codigo + '/recibos'; $r = Invoke-WebRequest -UseBasicParsing -Method Post -Uri ($ruta + '/reintentos') -Headers $h -TimeoutSec 30; Write-Host ('Reintento HTTP ' + [int]$r.StatusCode); $r.Content; $estado = Invoke-RestMethod -Uri $ruta -Headers $h -TimeoutSec 30; $estado.programacion | ConvertTo-Json -Depth 4 } finally { Invoke-RestMethod -Method Delete -Uri ($acceso + '/sesion') -Headers $h -TimeoutSec 30 | Out-Null } }"
```
