# Historial mensual de consumo

La segunda parte del bloque 4 permite consultar el consumo y la cobertura
de meses anteriores, según RF19. Nivora calcula cada periodo con las
lecturas aceptadas en su MySQL. La consulta usa el suministro de la sesión
y puede responder con los datos guardados durante una caída de la fuente.

## Contrato HTTP

`GET /api/v1/acceso/consulta/historial` requiere una sesión vigente de
tipo `CONSULTA`. Envía su token mediante `Authorization: Bearer …`.

| Parámetro opcional | Valor |
|---|---|
| `meses` | Entre 1 y 12 periodos consecutivos. Valor predeterminado de 12. |
| `hasta` | Último mes que se incluye, en formato `AAAA-MM`. Por defecto se usa el mes anterior al actual en Lima. |

Por ejemplo, `?meses=3&hasta=2026-10` devuelve octubre, septiembre y agosto
de 2026 en ese orden, siempre que octubre ya haya terminado. Para consultar
una ventana más antigua, cambia `hasta`.

La respuesta HTTP 200 contiene `fuente`, `codigoSuministro`, `zonaHoraria`,
`consultadoEn` y `periodos`, del más reciente al más antiguo. Cada elemento
incluye `periodo`, `inicioMes`, `finMesExclusivo` y `consumo`. Un rango
inválido devuelve HTTP 400. Una sesión ausente, inválida o cerrada devuelve
HTTP 401. Un fallo al consultar los datos locales devuelve HTTP 503. Todas
las respuestas del historial llevan `Cache-Control: no-store`.

## Cobertura del mes

Cada mes comienza a las 00:00 de `America/Lima`. Las fechas de la respuesta
se expresan en UTC. La lectura exacta del comienzo del mes siguiente sirve
como contador final del mes anterior y como contador inicial del siguiente.
Por ejemplo, la lectura de `2026-11-01T05:00:00Z` permite cerrar octubre y
abrir noviembre. El consumo se obtiene restando los contadores del tramo.

| Estado de `consumo` | Qué indica |
|---|---|
| `COMPLETO` | Hay lecturas compatibles en los dos límites exactos del mes. |
| `PARCIAL` | Hay al menos dos lecturas compatibles y falta uno o ambos límites. El monto corresponde a las fechas `desde` y `hasta`. |
| `SIN_DATOS` | El mes se devuelve con menos de dos lecturas disponibles y un consumo `null`. |
| `INCOMPATIBLE` | Se encontró un cambio de medidor o un retroceso del contador. El consumo se devuelve como `null`. |

Se entregan los litros y m³ como cadenas decimales exactas, con referencias
al medidor y a las lecturas usadas. El consumo cero corresponde a lecturas
compatibles con contadores iguales. Consulta el mes en curso en
[consumo actual](consumo-actual.md).

## Comprobación en Windows

Compila en CMD con `mvnw.cmd clean verify` desde
`C:\Users\HP\Desktop\Nivora\tecnologia\nivora-backend`.
Inicia Empresa de Agua en 8081 y arranca el JAR nuevo de Nivora en 8082
según los pasos de [sesiones de consulta](sesiones-consulta.md#probar-desde-windows).
En otro CMD, pega esta línea e ingresa el suministro de prueba y el DNI
cuando se soliciten. El comando imprime el historial y cierra la sesión.

```bat
powershell -NoProfile -Command "& { $ErrorActionPreference = 'Stop'; $codigo = Read-Host 'Codigo de suministro'; $seguro = Read-Host 'DNI de 8 digitos' -AsSecureString; $body = @{codigoSuministro=$codigo; dni=[System.Net.NetworkCredential]::new('', $seguro).Password} | ConvertTo-Json -Compress; $base = 'http://127.0.0.1:8082/api/v1/acceso/consulta'; $inicio = Invoke-RestMethod -UseBasicParsing -Method Post -Uri ($base + '/sesiones') -ContentType 'application/json' -Body $body -TimeoutSec 30; $cabecera = @{Authorization=('Bearer ' + $inicio.token)}; try { $r = Invoke-WebRequest -UseBasicParsing -Uri ($base + '/historial?meses=3') -Headers $cabecera -TimeoutSec 30; Write-Host ('Historial HTTP ' + [int]$r.StatusCode); $r.Content | ConvertFrom-Json | Select-Object zonaHoraria,periodos | ConvertTo-Json -Depth 7 } finally { Invoke-RestMethod -UseBasicParsing -Method Delete -Uri ($base + '/sesion') -Headers $cabecera -TimeoutSec 30 | Out-Null } }"
```

El resultado esperado empieza con `Historial HTTP 200`. Al ejecutar la
prueba en noviembre de 2026, devuelve octubre, septiembre y agosto.
El estado de cada mes depende de las lecturas guardadas para el suministro.
