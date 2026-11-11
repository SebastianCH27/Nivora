# Consumo acumulado del mes

La primera parte del bloque 4 implementa RF10–RF14 para lecturas acumuladas
aceptadas en MySQL. La consulta usa el suministro de la sesión `CONSULTA`
y puede seguir respondiendo con los datos almacenados durante una caída
de la Empresa de Agua o de APIsPERU. El inicio de sesión conserva sus
validaciones externas.

## Contrato HTTP

`GET /api/v1/acceso/consulta/consumo-actual` requiere
`Authorization: Bearer <token-de-consulta>`. El suministro se toma de la
sesión persistida. La ruta devuelve HTTP 200 con el periodo actual,
los límites del mes y el consumo con sus referencias. HTTP 401 indica que
se requiere una sesión de consulta vigente y HTTP 503 indica un fallo
al consultar los datos propios. Todas las respuestas llevan
`Cache-Control: no-store`.

El mes se determina al consultar en `America/Lima`. Las fechas se expresan
en UTC. El cálculo usa lecturas desde el inicio exacto del mes hasta la última
medición disponible a la hora de la consulta. Una lectura del mes anterior
se puede mostrar como última medición cuando el mes actual está vacío.

| Estado de `consumo` | Significado |
|---|---|
| `COMPLETO` | Hay una lectura al inicio del mes y al menos otra posterior. La diferencia cubre desde el inicio hasta la última medición disponible. |
| `PARCIAL` | Hay dos o más lecturas compatibles dentro del mes. Como falta la lectura del inicio exacto, el resultado cubre el tramo delimitado por `desde` y `hasta`. |
| `SIN_DATOS` | Hay menos de dos lecturas del mes. Los importes son `null`. |
| `INCOMPATIBLE` | Las lecturas del mes incluyen un cambio de medidor o un retroceso del contador. Los importes son `null`. |

Para un tramo calculable, `litros` es la diferencia de los contadores
y `metrosCubicos` es esa diferencia dividida entre 1000. Ambos se entregan
como cadenas decimales para conservar la precisión. Dos mediciones iguales
de un mismo contador producen `"0"` litros y `"0.000"` m³. Se conservan
`idMedidor`, `idLecturaInicial` e `idLecturaFinal` como referencias a la base.
El cálculo revisa también las mediciones intermedias. La cobertura del
acumulado corresponde al tramo medido, sin interpolar el inicio del mes.

| Estado de `actualizacion` | Significado |
|---|---|
| `CORTE_CONFIRMADO` | El recorrido guardado terminó con un corte que alcanza la fecha de la última lectura disponible. |
| `PENDIENTE` | Existen lecturas y falta una confirmación de recorrido que alcance la última medición. |
| `CON_INCIDENCIAS` | El progreso o el último resultado de sincronización registra un problema. El consumo local sigue disponible según su propia cobertura. |
| `SIN_LECTURAS` | Aún no hay una lectura aceptada para mostrar. |

`ultimaLectura` es la fecha de medición. `confirmadoHasta` corresponde al
corte del recorrido completado cuando está disponible. Las dos fechas pueden
diferir. El corte se confirma para su fecha registrada y el consumo solo llega
hasta la última medición utilizada.

## Comprobación desde Windows

Usa las tablas que ya tienes con los scripts 01 a 09. Compila en el CMD del
backend.

```bat
cd /d C:\Users\HP\Desktop\Nivora\tecnologia\nivora-backend
mvnw.cmd clean verify
```

Arranca Empresa de Agua en 8081 y Nivora en 8082 siguiendo los pasos de
[sesiones de consulta](sesiones-consulta.md#probar-desde-windows).
Deja abiertas ambas ventanas de servidor. En un tercer CMD, pega el comando
completo e ingresa el suministro de prueba y el DNI en el campo oculto.

```bat
powershell -NoProfile -Command "& { $ErrorActionPreference = 'Stop'; $codigo = Read-Host 'Codigo de suministro'; $seguro = Read-Host 'DNI de 8 digitos' -AsSecureString; $body = @{codigoSuministro=$codigo; dni=[System.Net.NetworkCredential]::new('', $seguro).Password} | ConvertTo-Json -Compress; $base = 'http://127.0.0.1:8082/api/v1/acceso/consulta'; $inicio = Invoke-RestMethod -UseBasicParsing -Method Post -Uri ($base + '/sesiones') -ContentType 'application/json' -Body $body -TimeoutSec 30; $cabecera = @{Authorization=('Bearer ' + $inicio.token)}; try { $resultado = Invoke-WebRequest -UseBasicParsing -Uri ($base + '/consumo-actual') -Headers $cabecera -TimeoutSec 30; Write-Host ('Consumo HTTP ' + [int]$resultado.StatusCode); $resultado.Content | ConvertFrom-Json | ConvertTo-Json -Depth 6 } finally { Invoke-RestMethod -UseBasicParsing -Method Delete -Uri ($base + '/sesion') -Headers $cabecera -TimeoutSec 30 | Out-Null } }"
```

La salida comienza con `Consumo HTTP 200`. El estado y las fechas dependen
de las lecturas guardadas para el mes local. La prueba imprime el consumo y
su cobertura, con el DNI y el token de sesión fuera de la salida.
