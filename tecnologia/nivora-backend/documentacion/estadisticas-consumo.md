# Comparación y estadísticas de consumo

La tercera parte del bloque 4 implementa la comparación de volúmenes de
RF20 y prepara los datos para los gráficos y las estadísticas de RF21.
El cálculo utiliza una sola consulta de las lecturas aceptadas en MySQL.
Los periodos mensuales usan el calendario de `America/Lima` y las fechas
de la respuesta se expresan en UTC.

## Contrato HTTP

`GET /api/v1/acceso/consulta/estadisticas?meses=3` requiere un token de una
sesión de tipo `CONSULTA`. El suministro procede de la sesión. El parámetro
opcional `meses` selecciona entre 1 y 12 meses anteriores. El valor
predeterminado es 12.

| Campo | Contenido |
|---|---|
| `comparacion.actual` | Acumulado del mes en curso hasta la última lectura disponible. |
| `comparacion.anterior` | Consumo del mes inmediatamente anterior según su cobertura. |
| `comparacion.diferencia` | Litros del tramo actual menos litros del anterior y su equivalente en m³. Es `null` si falta un consumo calculable. |
| `serieMensual` | Meses anteriores solicitados, en orden cronológico, con sus fechas, estados de cobertura y consumos. |
| `resumen` | Conteos por estado, total, promedio, mínimo y máximo de los meses completos de la serie. |

Los dos tramos comparados incluyen las fechas efectivamente medidas en
`consumo.desde` y `consumo.hasta`, además de `duracionSegundos`. La duración
conserva fracciones de segundo y queda como `null` si el consumo del tramo
es incalculable. Una diferencia negativa indica que el volumen medido del
tramo actual es menor. Consulta las duraciones antes de interpretar esa
diferencia, porque el acumulado actual puede abarcar menos días.

La serie conserva los meses sin datos o incompatibles con consumo `null`.
Un mes parcial incluye el consumo del tramo disponible. Un mes completo
con consumo cero cuenta para el promedio. El total, promedio, mínimo y
máximo quedan como `null` cuando la serie carece de meses completos. El
promedio se redondea a tres decimales de litro y la conversión a m³ divide
por 1000. Las cantidades se entregan como cadenas decimales.

La consulta exitosa devuelve HTTP 200. Un valor inválido de `meses` devuelve
HTTP 400 y una sesión ausente, vencida o cerrada devuelve HTTP 401. Si falla
la consulta a la base local, la API devuelve HTTP 503. Las respuestas
llevan `Cache-Control: no-store`.

## Comprobación en Windows

Compila en CMD con `mvnw.cmd clean verify` desde
`C:\Users\HP\Desktop\Nivora\tecnologia\nivora-backend`.
Deja Empresa de Agua en 8081 y arranca el JAR nuevo de Nivora en 8082 según
los pasos de [sesiones de consulta](sesiones-consulta.md#probar-desde-windows).
En otro CMD, pega la línea completa e ingresa el suministro de prueba y el
DNI cuando se soliciten. El comando imprime la comparación y el resumen,
luego cierra la sesión.

```bat
powershell -NoProfile -Command "& { $ErrorActionPreference = 'Stop'; $codigo = Read-Host 'Codigo de suministro'; $seguro = Read-Host 'DNI de 8 digitos' -AsSecureString; $body = @{codigoSuministro=$codigo; dni=[System.Net.NetworkCredential]::new('', $seguro).Password} | ConvertTo-Json -Compress; $base = 'http://127.0.0.1:8082/api/v1/acceso/consulta'; $inicio = Invoke-RestMethod -UseBasicParsing -Method Post -Uri ($base + '/sesiones') -ContentType 'application/json' -Body $body -TimeoutSec 30; $cabecera = @{Authorization=('Bearer ' + $inicio.token)}; try { $r = Invoke-WebRequest -UseBasicParsing -Uri ($base + '/estadisticas?meses=3') -Headers $cabecera -TimeoutSec 30; Write-Host ('Estadisticas HTTP ' + [int]$r.StatusCode); $r.Content | ConvertFrom-Json | Select-Object comparacion,resumen | ConvertTo-Json -Depth 7 } finally { Invoke-RestMethod -UseBasicParsing -Method Delete -Uri ($base + '/sesion') -Headers $cabecera -TimeoutSec 30 | Out-Null } }"
```

El resultado esperado empieza con `Estadisticas HTTP 200`. En noviembre
de 2026, compara el acumulado de noviembre con octubre y resume agosto,
septiembre y octubre. Los valores dependen de las lecturas guardadas.
