# Supervisión administrativa de recibos

`GET /api/v1/supervision/suministros/{codigo}/recibos` consulta los recibos
guardados y el estado de su sincronización desde Empresa de Agua. Requiere
una sesión administrativa con el permiso `SUPERVISAR_INTEGRACION`.

La consulta lee las tablas creadas por `12-recibos.sql` y
`13-sincronizacion-recibos.sql`. Funciona con los datos registrados en Nivora
durante una interrupción de Empresa de Agua y no inicia otra sincronización.

## Contenido de la respuesta

| Campo | Contenido |
| --- | --- |
| `fuente`, `codigoSuministro` | Fuente de la integración y código del suministro registrado. |
| `consultadoEn` | Fecha y hora UTC de la consulta. |
| `sincronizacionAutomaticaHabilitada` | Valor de la configuración del proceso automático. |
| `almacenamiento.recibosGuardados` | Cantidad de recibos guardados para ese suministro. |
| `almacenamiento.ultimoPeriodo` | Mes más reciente entre los recibos guardados, en formato `AAAA-MM`. |
| `almacenamiento.ultimaRecepcion` | Fecha y hora UTC en que se incorporó el último recibo recibido. |
| `programacion.ultimoResultado`, `programacion.fallosConsecutivos` | Resultado confirmado del último ciclo y cantidad de fallos consecutivos. |
| `programacion.ultimoIntento`, `programacion.ultimoExito`, `programacion.proximoIntento` | Fechas UTC del último ciclo confirmado, del último éxito y del siguiente intento previsto. |
| `programacion.reservaHasta`, `programacion.reservaVigente`, `programacion.reservaVencida` | Límite UTC de la reserva de trabajo y su estado al consultar. |

Un suministro sin recibos devuelve cero y valores nulos para el último periodo
y la última recepción. `programacion` es nulo hasta que se registra el calendario
del suministro. Si un ciclo confirma recibos que ya estaban guardados, avanza
`ultimoExito` y se conserva `ultimaRecepcion`.

La respuesta usa `Cache-Control: no-store`. Un código con formato inválido
devuelve `400`. El acceso sin una sesión administrativa válida devuelve
`401`, y una sesión administrativa sin el permiso devuelve `403`. Un suministro
ausente devuelve `404` y un fallo en la consulta local devuelve `503`.

## Comprobación local

Con Nivora en el puerto `8082`, pega esta línea en un CMD separado. Usa un
suministro ya registrado. El comando pide la clave administrativa sin mostrarla
y cierra la sesión al terminar.

```cmd
powershell -NoProfile -Command "& { $ErrorActionPreference = 'Stop'; $usuario = Read-Host 'Usuario administrativo'; $clave = Read-Host 'Clave administrativa' -AsSecureString; $codigo = Read-Host 'Codigo de suministro'; if ($codigo -notmatch '^[0-9]{1,20}$') { throw 'Escribe el codigo de suministro valido.' }; $body = @{usuario=$usuario; clave=[System.Net.NetworkCredential]::new('', $clave).Password} | ConvertTo-Json -Compress; $api = 'http://127.0.0.1:8082/api/v1'; $acceso = $api + '/acceso/administradores'; $inicio = Invoke-RestMethod -Method Post -Uri ($acceso + '/sesiones') -ContentType 'application/json' -Body $body -TimeoutSec 30; $h = @{Authorization='Bearer ' + $inicio.token}; try { $ruta = $api + '/supervision/suministros/' + $codigo + '/recibos'; $r = Invoke-WebRequest -UseBasicParsing -Uri $ruta -Headers $h -TimeoutSec 30; Write-Host ('Supervision de recibos HTTP ' + [int]$r.StatusCode); $r.Content } finally { Invoke-RestMethod -Method Delete -Uri ($acceso + '/sesion') -Headers $h -TimeoutSec 30 | Out-Null } }"
```
