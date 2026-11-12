# Consulta de recibos del suministro

`GET /api/v1/acceso/consulta/recibos` requiere una sesión de consulta
vigente. Nivora identifica el suministro mediante el token de sesión y
lee los recibos almacenados en MySQL. Las consultas de una sesión ya
iniciada pueden continuar durante un fallo de Empresa de Agua o de la
API de identidad.

| Parámetro | Regla |
|---|---|
| `limite` | Cantidad de recibos por página, de 1 a 60. El valor predeterminado es 12. Usa 1 para consultar el recibo más reciente. |
| `antesDe` | Mes exclusivo en formato `AAAA-MM`. Sin este parámetro, la consulta comienza por el mes más reciente disponible. |

La respuesta ordena los recibos del más reciente al más antiguo. Cuando
`hayMas` es `true`, envía `siguienteAntesDe` como `antesDe` para consultar
la página siguiente. Si el suministro carece de recibos guardados,
la API devuelve HTTP 200 con un arreglo vacío.

Cada recibo incluye `numeroRecibo`, `periodo`, consumo, cargos, referencia
tarifaria, `importeTotal` definitivo y `fechaVencimiento`. Los montos
se envían como cadenas decimales en PEN. `fechaDisponibilidad` y
`fechaVencimiento` se expresan en el calendario de Lima. `recibidoEn`
es el instante de primera recepción en Nivora, en UTC.

| Respuesta | Significado |
|---|---|
| HTTP 200 | Consulta válida, con recibos o con un arreglo vacío. |
| HTTP 400 | Límite o mes de corte inválido. |
| HTTP 401 | Se requiere una sesión de consulta vigente. |
| HTTP 503 | No se pudo consultar el almacenamiento local. |

## Comprobación en Windows

Compila con `mvnw.cmd clean verify` y arranca Nivora en 8082 siguiendo
[los pasos de inicio](sesiones-consulta.md#probar-desde-windows).
Con el suministro de prueba y su DNI, ejecuta este comando en otro CMD.
La consulta muestra el recibo más reciente y cierra la sesión.

```bat
powershell -NoProfile -Command "& { $ErrorActionPreference = 'Stop'; $codigo = Read-Host 'Codigo de suministro'; $seguro = Read-Host 'DNI de 8 digitos' -AsSecureString; $body = @{codigoSuministro=$codigo; dni=[System.Net.NetworkCredential]::new('', $seguro).Password} | ConvertTo-Json -Compress; $base = 'http://127.0.0.1:8082/api/v1/acceso/consulta'; $inicio = Invoke-RestMethod -UseBasicParsing -Method Post -Uri ($base + '/sesiones') -ContentType 'application/json' -Body $body -TimeoutSec 30; $cabecera = @{Authorization=('Bearer ' + $inicio.token)}; try { $r = Invoke-WebRequest -UseBasicParsing -Uri ($base + '/recibos?limite=1') -Headers $cabecera -TimeoutSec 30; Write-Host ('Recibos HTTP ' + [int]$r.StatusCode); $r.Content | ConvertFrom-Json | ConvertTo-Json -Depth 7 } finally { Invoke-RestMethod -UseBasicParsing -Method Delete -Uri ($base + '/sesion') -Headers $cabecera -TimeoutSec 30 | Out-Null } }"
```

Para el suministro `13100390` con el recibo de octubre de 2026, la
respuesta incluye `importeTotal` de `"13.46"` y `fechaVencimiento` de
`"2026-11-08"` mientras sea el recibo más reciente guardado.
