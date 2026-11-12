# Fuente de recibos de Empresa de Agua

La primera parte del bloque 6 consulta la ruta
`GET /api/v1/suministros/{codigo}/recibos?limite=12` de Empresa de Agua.
El límite admite de 1 a 60 recibos. La fuente ordena los recibos del más
reciente al más antiguo. Nivora comprueba el código de suministro, la
moneda PEN, los periodos, las fechas y la coherencia de los importes.
Preserva el identificador, el consumo, los cargos, el importe definitivo
y la referencia tarifaria que acompañan al recibo.

El periodo `2026-10-01` del proveedor corresponde al mes `2026-10`.
Las fechas son comerciales y usan el calendario de `America/Lima`.
El vencimiento se toma del recibo emitido. En la simulación, Empresa de
Agua lo fija siete días después de la disponibilidad. El importe
definitivo procede de la misma respuesta de Empresa de Agua.

| Resultado | Interpretación |
|---|---|
| HTTP 200 con recibos válidos | Recibos emitidos y disponibles para el suministro. |
| HTTP 200 con lista vacía | Suministro existente sin recibos disponibles. |
| HTTP 404 que identifica la ruta y el suministro como inexistente | Ausencia confirmada en Empresa de Agua. |
| Error de conexión, HTTP 503 o respuesta inconsistente | Consulta de recibos no disponible. |

## Comprobar en Windows

Ejecuta `mvnw.cmd clean verify` desde `tecnologia\nivora-backend`. Con
Empresa de Agua en el puerto 8081, ejecuta el JAR en modo de comprobación.
Esta ejecución solo consulta Empresa de Agua y finaliza al terminar.

```bat
java -jar target\nivora-backend-0.0.1-SNAPSHOT.jar --spring.main.web-application-type=none --spring.datasource.password= --nivora.sincronizacion.habilitada=false --nivora.recibos.comprobar-al-iniciar=true
```

La consulta predeterminada usa el suministro de demostración `13100390`.
La línea `Recibo emitido:` muestra el periodo, el importe total en PEN,
la fecha de disponibilidad y el vencimiento del recibo más reciente.
Usa `--nivora.recibos.codigo-prueba=CODIGO` para elegir otro suministro.
