# Proyección mensual de consumo y costo

`GET /api/v1/acceso/consulta/proyeccion` usa el suministro de una sesión
`CONSULTA` vigente. El periodo corresponde al mes actual de `America/Lima`.
La fecha `calculadoEn` indica cuándo se consultaron los datos. El campo
`observado.hasta` indica cuándo se tomó la última medición usada.
`actualizacion` informa el estado de sincronización de esas lecturas.

## Cálculo

Se requieren lecturas compatibles desde el inicio del mes y al menos
86 400 segundos de observación. El cálculo mantiene el ritmo promedio
observado hasta el cierre del mes. Multiplica los litros observados por
la duración del mes y divide entre el tiempo observado. Redondea el
resultado al litro más próximo con `HALF_UP` y lo convierte a m³. El
promedio diario se muestra con tres decimales. El cálculo del volumen
usa la duración exacta de las lecturas, incluida la fracción de segundo.

Por ejemplo, 4000 L observados durante los primeros 10 días de un mes
de 30 días dan una proyección de 12 000 L (12 m³).

| Estado | Significado |
|---|---|
| `PROYECTADO` | El volumen estimado está en `proyeccion`. |
| `SIN_CONSUMO_CALCULABLE` | Las lecturas son insuficientes o incompatibles. `observado.motivo` indica la causa. |
| `COBERTURA_INCOMPLETA` | Falta cobertura desde el inicio del mes. |
| `TIEMPO_INSUFICIENTE` | El intervalo observado dura menos de un día. |
| `PERIODO_CERRADO` | La medición alcanza el cierre del periodo. |

Cuando existe una proyección, el costo se calcula sobre los litros
proyectados, con los tramos y el cargo fijo mensual de la asignación
que cubre todo el mes. La respuesta incluye la revisión tarifaria
guardada y el desglose en PEN. El monto a pagar se consulta en el
recibo emitido por la empresa.

| Estado de `costoProyectado` | Significado |
|---|---|
| `ESTIMADO` | Se pudo calcular el total proyectado. |
| `CONCEPTOS_INCOMPLETOS` | Hay un `subtotalConocido` y `totalEstimado` es `null`. |
| `SIN_TARIFA_COMPATIBLE` | Se proyectó el volumen y falta una asignación que cubra todo el mes. |

La tarifa de demostración `DOMESTICA` v1 informa agua y cargo fijo.
Con esa versión, el subtotal conocido incluye ambos conceptos. El
alcantarillado, el IGV y el total estimado aparecen como `null`.

## Comprobación en Windows

Se reutilizan las tablas de lecturas y tarifas ya instaladas. Compila
con `mvnw.cmd clean verify` y arranca el nuevo JAR de Nivora en 8082,
con Empresa de Agua en 8081, siguiendo
[los pasos de inicio](sesiones-consulta.md#probar-desde-windows).
En otro CMD ejecuta la consulta. El comando pide el suministro y el
DNI, muestra la proyección y cierra la sesión.

```bat
powershell -NoProfile -Command "& { $ErrorActionPreference = 'Stop'; $codigo = Read-Host 'Codigo de suministro'; $seguro = Read-Host 'DNI de 8 digitos' -AsSecureString; $body = @{codigoSuministro=$codigo; dni=[System.Net.NetworkCredential]::new('', $seguro).Password} | ConvertTo-Json -Compress; $base = 'http://127.0.0.1:8082/api/v1/acceso/consulta'; $inicio = Invoke-RestMethod -UseBasicParsing -Method Post -Uri ($base + '/sesiones') -ContentType 'application/json' -Body $body -TimeoutSec 30; $cabecera = @{Authorization=('Bearer ' + $inicio.token)}; try { $r = Invoke-WebRequest -UseBasicParsing -Uri ($base + '/proyeccion') -Headers $cabecera -TimeoutSec 30; Write-Host ('Proyeccion HTTP ' + [int]$r.StatusCode); $r.Content | ConvertFrom-Json | Select-Object periodo,calculadoEn,estado,observado,proyeccion,costoProyectado | ConvertTo-Json -Depth 8 } finally { Invoke-RestMethod -UseBasicParsing -Method Delete -Uri ($base + '/sesion') -Headers $cabecera -TimeoutSec 30 | Out-Null } }"
```
