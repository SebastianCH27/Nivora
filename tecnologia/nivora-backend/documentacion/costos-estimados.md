# Costos estimados del consumo

`GET /api/v1/acceso/consulta/costos?meses=3` devuelve el costo estimado
del consumo actual y de los meses anteriores, del más reciente al más
antiguo. La consulta usa el suministro de la sesión `CONSULTA`. El parámetro
`meses` admite de 1 a 12 meses anteriores y su valor predeterminado es 3.

El cálculo usa las lecturas guardadas y las revisiones vigentes al
consultar. Cada mes se evalúa con una asignación que cubra todo su intervalo
en `America/Lima`. Si existe una vigencia administrativa durante el mes,
la asignación debe venir de esa revisión. Si dos vigencias se suceden a
mitad de mes, el sistema informa que el mes carece de una tarifa compatible.

| Estado de la estimación | Qué devuelve |
|---|---|
| `ESTIMADO` | Consumo medido, tarifa usada y total estimado en PEN. |
| `CONCEPTOS_INCOMPLETOS` | Desglose disponible y `subtotalConocido`. El total estimado es `null`. |
| `SIN_CONSUMO_CALCULABLE` | Motivo del estado de consumo. |
| `SIN_TARIFA_COMPATIBLE` | Consumo calculable para un periodo sin una asignación aplicable a todo el mes. |

Cada resultado incluye la cobertura del consumo y las referencias a las
lecturas. Un consumo parcial se calcula sobre el tramo medido y se marca
como tal. El cargo fijo corresponde al mes completo. El campo principal
`revisionTarifaria` identifica la revisión importada y
`revisionTarifariaAdministrativa` identifica la publicación administrativa.
Cada tarifa aplicada indica su fuente, revisión, código, versión y fechas.
Los volúmenes y montos se entregan como cadenas decimales.

## Cálculo

Se divide el volumen en litros entre 1000 y se aplica cada precio a la
porción que corresponde a su tramo. Se suman las porciones de cada servicio
y luego se redondea cada servicio a céntimos con `HALF_UP`. Los importes de
cada tramo se muestran antes de ese redondeo. El IGV se calcula sobre agua,
alcantarillado y cargo fijo, usando la tasa guardada. Se redondea a
céntimos y se suma al subtotal. El subtotal conocido incluye el agua, el
cargo fijo y el alcantarillado cuando su importe completo se puede calcular.

La tarifa `DOMESTICA` importada de la Empresa de Agua simulada aporta
agua y cargo fijo. Los conceptos omitidos aparecen como `null`. Un
concepto cuyo precio está definido como cero se calcula con ese valor.
El importe es una estimación del consumo medido. El monto a pagar procede
del recibo emitido por la empresa.

## Referencia de Huamanga para pruebas

El 11 de noviembre de 2026 se consultó Yakúmetro para Huamanga, categoría
doméstica, con medidor, agua y alcantarillado, sin subsidio focalizado.
Las pruebas usan los precios por tramo y el cargo fijo de S/ 3,60
mostrados en el simulador. El cuadro de SUNASS publicado en El Peruano
indica S/ 3,62 para el cargo fijo base antes de impuestos, sujeto a
reajuste. La configuración de un suministro real requiere los valores
aplicables a su ciclo de facturación.
[Resolución de SUNASS](https://busquedas.elperuano.pe/dispositivo/NL/2424753-1)

| Volumen | Nivora con los valores de prueba | Yakúmetro observado |
|---|---:|---:|
| 12 m³ | S/ 28,70 | S/ 28,70 |
| 18 m³ | S/ 41,72 | S/ 41,73 |
| 20 m³ | S/ 48,52 | S/ 48,52 |

En 18 m³, el resultado de Yakúmetro corresponde a la opción de escenario
del 90 % de 20 m³. La diferencia de un céntimo queda registrada. Nivora
usa el redondeo descrito en esta página.
[Ver simulación de 20 m³](https://apps.sunass.gob.pe/simulador-tarifario/#/resultado?localidad=8280&empresa=1635&tiposervicio=3&categoria=419&modalidad=1&volumen1=20&volumen2=null&checkSCF=false)

## Comprobación en Windows

Usa la tarifa guardada siguiendo [la incorporación de tarifas](almacenamiento-tarifas.md).
Compila con `mvnw.cmd clean verify` y arranca el nuevo JAR de Nivora en
8082 con Empresa de Agua en 8081, según los pasos de
[sesiones de consulta](sesiones-consulta.md#probar-desde-windows).
Desde otro CMD, ejecuta este comando con el suministro de prueba y tu DNI.

```bat
powershell -NoProfile -Command "& { $ErrorActionPreference = 'Stop'; $codigo = Read-Host 'Codigo de suministro'; $seguro = Read-Host 'DNI de 8 digitos' -AsSecureString; $body = @{codigoSuministro=$codigo; dni=[System.Net.NetworkCredential]::new('', $seguro).Password} | ConvertTo-Json -Compress; $base = 'http://127.0.0.1:8082/api/v1/acceso/consulta'; $inicio = Invoke-RestMethod -UseBasicParsing -Method Post -Uri ($base + '/sesiones') -ContentType 'application/json' -Body $body -TimeoutSec 30; $cabecera = @{Authorization=('Bearer ' + $inicio.token)}; try { $r = Invoke-WebRequest -UseBasicParsing -Uri ($base + '/costos?meses=1') -Headers $cabecera -TimeoutSec 30; Write-Host ('Costos HTTP ' + [int]$r.StatusCode); $r.Content | ConvertFrom-Json | Select-Object moneda,revisionTarifaria,actual,anteriores | ConvertTo-Json -Depth 8 } finally { Invoke-RestMethod -UseBasicParsing -Method Delete -Uri ($base + '/sesion') -Headers $cabecera -TimeoutSec 30 | Out-Null } }"
```

Con la tarifa de demostración y el consumo de octubre de 5637 litros,
octubre muestra `CONCEPTOS_INCOMPLETOS` y un subtotal conocido de S/ 13,46.
