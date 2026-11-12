# Bandeja de notificaciones

La bandeja reúne las alertas de consumo y los avisos de recibo guardados.
Aplica `base-datos/16-notificaciones-leidas.sql` después de los scripts 14 y 15.
Los avisos existentes aparecen como pendientes hasta que se marquen como
leídos. La marca y la primera fecha de lectura se conservan por suministro
entre sesiones.

## Contrato HTTP

Usa el encabezado `Authorization: Bearer ...` de una sesión de consulta vigente.
La API toma el suministro de la sesión. Todas las respuestas incluyen
`Cache-Control: no-store`.

| Método y ruta | Resultado |
|---|---|
| `GET /api/v1/acceso/consulta/notificaciones` | HTTP 200 con una página de avisos. El arreglo es vacío cuando el filtro carece de resultados. |
| `PUT /api/v1/acceso/consulta/notificaciones/{id}/leida` | HTTP 204 al marcar como leído un aviso. Repetir la operación conserva la primera fecha y devuelve HTTP 204. |

| Parámetro de consulta | Regla |
|---|---|
| `limite` | De 1 a 60 avisos por página, 20 por defecto. |
| `soloPendientes` | `true` muestra los avisos sin marca de lectura. Por defecto se muestran ambos estados. |
| `antesDe` | Pasa el valor de `siguienteAntesDe` de la página anterior y conserva el mismo filtro. |

La lista se ordena por fecha de registro descendente. Los empates se
resuelven por tipo y periodo descendentes. Cada elemento incluye `id`,
`tipo`, `periodo`, `titulo`, `registradaEn`, `leida` y `leidaEn`. Las
alertas `ALERTA_CONSUMO` incluyen los litros actual y anterior como texto
en `consumo`. Los avisos `RECIBO_DISPONIBLE` incluyen el número y las
fechas de disponibilidad y vencimiento del recibo en `recibo`. Los
instantes se expresan en UTC y las fechas del recibo en el calendario de
Lima.

Una solicitud inválida devuelve HTTP 400. Una sesión ausente, caducada o
revocada devuelve HTTP 401. Marcar un identificador válido que el suministro
no tenga en su bandeja devuelve HTTP 404. Una falla en el acceso a los datos
devuelve HTTP 503.

## Comprobación en Windows

Compila con `mvnw.cmd clean verify` e inicia ambos servicios según
[los pasos de sesiones de consulta](sesiones-consulta.md#probar-desde-windows).
En un tercer CMD, ejecuta la comprobación con un suministro que tenga un
aviso. La prueba marca como leído el primer aviso de la lista, verifica que
se conserve la fecha al repetir la operación y cierra la sesión.

```bat
powershell -NoProfile -Command "& { $ErrorActionPreference = 'Stop'; $codigo = Read-Host 'Codigo de suministro'; $seguro = Read-Host 'DNI de 8 digitos' -AsSecureString; $body = @{codigoSuministro=$codigo; dni=[System.Net.NetworkCredential]::new('', $seguro).Password} | ConvertTo-Json -Compress; $base = 'http://127.0.0.1:8082/api/v1/acceso/consulta'; $inicio = Invoke-RestMethod -UseBasicParsing -Method Post -Uri ($base + '/sesiones') -ContentType 'application/json' -Body $body -TimeoutSec 30; $cabecera = @{Authorization=('Bearer ' + $inicio.token)}; try { $bandeja = $base + '/notificaciones'; $lista = Invoke-RestMethod -UseBasicParsing -Uri $bandeja -Headers $cabecera -TimeoutSec 30; if ($lista.notificaciones.Count -eq 0) { throw 'El suministro no tiene avisos guardados.' }; $aviso = $lista.notificaciones[0]; Write-Host ('Aviso ' + $aviso.id + ' | leida antes: ' + $aviso.leida); $ruta = $bandeja + '/' + $aviso.id + '/leida'; $marca = Invoke-WebRequest -UseBasicParsing -Method Put -Uri $ruta -Headers $cabecera -TimeoutSec 30; Write-Host ('Marca HTTP ' + [int]$marca.StatusCode); $leido = (Invoke-RestMethod -UseBasicParsing -Uri $bandeja -Headers $cabecera -TimeoutSec 30).notificaciones | Where-Object { $_.id -eq $aviso.id }; $null = Invoke-WebRequest -UseBasicParsing -Method Put -Uri $ruta -Headers $cabecera -TimeoutSec 30; $repetido = (Invoke-RestMethod -UseBasicParsing -Uri $bandeja -Headers $cabecera -TimeoutSec 30).notificaciones | Where-Object { $_.id -eq $aviso.id }; if (-not $leido.leida -or -not $leido.leidaEn -or $leido.leidaEn -ne $repetido.leidaEn) { throw 'No se conservo la primera marca de lectura.' }; $leido | Select-Object id,leida,leidaEn,recibo | ConvertTo-Json -Depth 4 } finally { Invoke-RestMethod -UseBasicParsing -Method Delete -Uri ($base + '/sesion') -Headers $cabecera -TimeoutSec 30 | Out-Null } }"
```
