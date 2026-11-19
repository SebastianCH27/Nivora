# Comparación de consumo y costos entre periodos

RF20/P02, ampliación sobre `cf2f699`, 19 de noviembre de 2026, America/Lima.

La API compara dos periodos mensuales del mismo suministro. Devuelve
fechas, duración, consumo, tarifa, desglose y diferencias. Utiliza las
lecturas y tarifas guardadas en Nivora, con las mismas reglas de costos
que la consulta existente. El importe definitivo se consulta en el recibo;
los valores de esta operación son estimaciones.

## Solicitud y alcance

`GET /api/v1/acceso/consulta/comparacion` requiere una sesión `CONSULTA`
vigente. El suministro procede de la sesión y no se modifica enviando
`codigoSuministro` en la URL. Las respuestas llevan `Cache-Control: no-store`.

| Parámetro | Comportamiento |
|---|---|
| `periodoBase` | Mes de referencia, `AAAA-MM`. Sus valores se restan a los del comparado. |
| `periodoComparado` | Mes que se quiere comparar, `AAAA-MM`. |
| Ambos ausentes | Base = mes anterior; comparado = mes actual según el calendario configurado. |
| Ambos presentes | Permite elegir meses anteriores o el actual. Los años comienzan en 1000 y la separación máxima es de 12 meses. |
| Solo uno presente, formato inválido o periodo futuro | HTTP 400. |

El límite de separación acota la lectura a un máximo de 13 periodos.
Se permite invertir el orden o comparar el mismo mes consigo mismo.
Un periodo civil que todavía no comienza según una regla mensual publicada
se considera futuro, aunque el mes ya haya comenzado en el calendario civil.

Ejemplo de dos meses cerrados:

```text
/api/v1/acceso/consulta/comparacion?periodoBase=2026-09&periodoComparado=2026-10
```

Ambos periodos se resuelven con una sola consulta de lecturas, un mismo
calendario y reloj, y una selección compartida del historial tarifario.
Cada estimación conserva la fuente, revisión y versión que le corresponde.
Las fechas del resultado se expresan en UTC y el calendario es America/Lima.

## Respuesta

| Campo | Contenido |
|---|---|
| `base.estimacion`, `comparado.estimacion` | Periodo, límites, estado de costo, consumo con cobertura y referencias, tarifa y desglose. Usan el mismo DTO de `/costos`. |
| `base.enCurso`, `comparado.enCurso` | El periodo de análisis todavía no ha terminado. |
| `base.periodoCompleto`, `comparado.periodoCompleto` | Periodo cerrado con consumo completo en ambos límites exactos. |
| `duracionPeriodoSegundos` en cada tramo | Duración de su periodo de análisis completo. |
| `duracionTramoSegundos` en cada tramo | Separación entre los extremos de consumo conocidos; null si no hay consumo calculable. Con huecos no representa tiempo totalmente cubierto. |
| `diferenciaLitros`, `diferenciaMetrosCubicos` | Consumo comparado menos consumo base; null si alguno no puede calcularse. |
| `diferenciaCostoEstimado` | Total estimado comparado menos total estimado base; null si falta un total o un consumo. |
| `mismaDuracionTramo` | Compara la duración entre extremos; null si alguna duración es desconocida. |
| `mismaVersionTarifaria` | Compara la fuente y la versión tarifaria seleccionada, incluidos sus conceptos. null si falta una selección aplicable. |
| `revisionTarifaria`, `revisionTarifariaAdministrativa` | Revisiones del historial disponible al consultar. Las selecciones por periodo se conservan dentro de cada estimación. |

Volúmenes, montos, duraciones y revisiones se publican como cadenas
decimales; los indicadores son booleanos o null según el contrato.
Las diferencias conservan el signo y los montos utilizan los totales ya
redondeados por las reglas de costos, sin un redondeo intermedio adicional.

| `estado` de comparación | Significado |
|---|---|
| `COMPLETA` | Ambos periodos están cerrados y completamente cubiertos y ambos totales estimados están disponibles. |
| `PARCIAL` | Los consumos y totales permiten obtener diferencias, pero un periodo sigue en curso o tiene cobertura incompleta. |
| `NO_CALCULABLE` | Falta un consumo o un total estimado. El importe de diferencia es null; puede conservarse la diferencia de volumen cuando ambos consumos son conocidos. |

Una cobertura `COMPLETO` del consumo actual solo acredita el tramo hasta
la última medición: `periodoCompleto` sigue siendo false mientras el mes
esté en curso. La comparación no proyecta, prorratea ni normaliza por días.
Dos meses completos pueden tener distinta duración o distintas tarifas.
Esas diferencias deben revisarse antes de interpretar el cambio de importe
como un cambio del ritmo de consumo.

Los huecos y los intervalos que cruzan un corte conservan las reglas de
[lecturas de intervalo](lecturas-intervalo.md). Los subtotales con conceptos
faltantes no se utilizan como si fueran totales. Una tarifa de demostración
sin alcantarillado o IGV definido puede producir `NO_CALCULABLE`; el detalle
indica `CONCEPTOS_INCOMPLETOS` y mantiene los conceptos conocidos.

## Ejemplos verificados

Con tarifa ficticia de agua S/ 2,50 por m³, alcantarillado e IGV definidos
en cero y cargo fijo S/ 5,00:

| Caso | Base | Comparado | Diferencia | Estado |
|---|---|---|---|---|
| Septiembre y octubre cerrados | 10 000 L; S/ 30,00 | 13 200 L; S/ 38,00 | +3200 L; +S/ 8,00 | COMPLETA |
| Orden invertido | 13 200 L; S/ 38,00 | 10 000 L; S/ 30,00 | −3200 L; −S/ 8,00 | COMPLETA |
| Octubre cerrado y noviembre hasta el día 11 | 10 000 L; S/ 30,00 | 3200 L; S/ 13,00 | −6800 L; −S/ 17,00 | PARCIAL |
| Un mes cerrado con huecos | 2000 L; S/ 10,00 | 2400 L conocidos; S/ 11,00 | +400 L conocidos; +S/ 1,00 | PARCIAL |

Septiembre y octubre tienen 30 y 31 días. Ambos pueden producir una
comparación completa con `mismaDuracionTramo=false`. Otro caso mantiene
10 000 L en ambos meses y cambia la tarifa de S/ 2,50 a S/ 3,30 por m³:
el costo sube S/ 8,00 y `mismaVersionTarifaria=false` explica la diferencia.

También se comprueban valores cero, importes negativos en la diferencia,
datos ausentes, conceptos incompletos, límites de volumen, cruces mensuales,
calendario con inicio el día 5, parámetros inválidos, estados HTTP y sesiones
independientes de dos suministros. La comparación se incluye en las pruebas
de continuidad durante fallos de Empresa de Agua, identidad y Web Push.

## Validación realizada

`clean verify` terminó con 632 pruebas en 35 clases, cero fallos, errores
u omitidas y JAR empaquetado. Son 30 casos más que en `cf2f699`:
ocho de dominio, 21 HTTP de comparación y un caso adicional en la matriz
de aislamiento de consultas. Los dos casos de continuidad existentes se
ampliaron para incluir la octava consulta.

Con MySQL 8.0.46 aislado pasaron los 31 casos HTTP seleccionados:
21 de comparación, ocho de contratos de consulta y dos de continuidad.
Los tres controles de OpenAPI forman parte de la regresión. El contrato
actual tiene 41 operaciones de negocio y una de salud, 104 esquemas,
64 registros y 16 enums Java enlazados.

Los métodos, resultados y huellas están en
[la evidencia de comparación](evidencias/comparacion-costos-2026-11-19.json).
La carga histórica de 5000 usuarios conserva su JAR y sus siete operaciones
originales; esta nueva ruta no está acreditada por aquella ejecución.
Las evaluaciones de infraestructura y de interfaces conservan sus reservas.

Con RF10/P01 y RF20/P02 implementados se completan las dos brechas funcionales
identificadas en la revisión del backend. La interfaz, HTTPS, escalabilidad
controlada y las demás evaluaciones pendientes siguen en
[la matriz RF/EQ](matriz-requisitos-backend.md).

## Comprobar en Windows

Copiar los archivos del paquete y reconstruir con Nivora detenido:

```bat
cd /d C:\Users\HP\Desktop\Nivora\tecnologia\nivora-backend
mvnw.cmd clean verify
```

Esta entrega utiliza el esquema existente, incluida la migración 21 ya
aplicada para intervalos. Las pruebas utilizan H2 y fuentes ficticias.
La consulta manual requiere MySQL, Empresa de Agua en 8081 y el JAR nuevo
de Nivora en 8082, según [los pasos de inicio](sesiones-consulta.md#probar-desde-windows).

Desde otro CMD, la siguiente línea solicita el suministro y el DNI, consulta
la comparación predeterminada y cierra la sesión. Los importes y estados
dependen de la cobertura y las tarifas guardadas:

```bat
powershell -NoProfile -Command "& { $ErrorActionPreference = 'Stop'; $codigo = Read-Host 'Codigo de suministro'; $seguro = Read-Host 'DNI de 8 digitos' -AsSecureString; $body = @{codigoSuministro=$codigo; dni=[System.Net.NetworkCredential]::new('', $seguro).Password} | ConvertTo-Json -Compress; $base = 'http://127.0.0.1:8082/api/v1/acceso/consulta'; $inicio = Invoke-RestMethod -Method Post -Uri ($base + '/sesiones') -ContentType 'application/json' -Body $body -TimeoutSec 30; $h = @{Authorization='Bearer ' + $inicio.token}; try { $r = Invoke-WebRequest -UseBasicParsing -Uri ($base + '/comparacion') -Headers $h -TimeoutSec 30; Write-Host ('Comparacion HTTP ' + [int]$r.StatusCode); $r.Content | ConvertFrom-Json | ConvertTo-Json -Depth 10 } finally { Invoke-RestMethod -Method Delete -Uri ($base + '/sesion') -Headers $h -TimeoutSec 30 | Out-Null } }"
```

Para elegir otros meses, añadir ambos parámetros a `/comparacion`, según
el ejemplo de la solicitud. El [contrato OpenAPI](openapi.json) conserva
los campos, nulabilidad, permisos y respuestas de esta ruta.
