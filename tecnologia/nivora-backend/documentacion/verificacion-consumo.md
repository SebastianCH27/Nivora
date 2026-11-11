# Verificación del bloque 4 de consumo

Las consultas de consumo actual, historial y estadísticas usan las lecturas
aceptadas para el suministro de la sesión. El caso integrado
`consumoHistorialYEstadisticasCoincidenAlCambiarElMesDeLima` ejecuta las tres
rutas HTTP con un reloj controlado. Las lecturas pasan por el repositorio de
incorporación y las consultas comprueban sus fechas e identificadores.

| Momento de la prueba en Lima | Resultado verificado |
|---|---|
| Un microsegundo antes del 1 de noviembre | Octubre sigue siendo el mes actual. El consumo acumulado es 150 litros y el historial empieza en septiembre. |
| Después de medianoche, antes de incorporar el cierre | Noviembre figura con `SIN_DATOS` y conserva la fecha de la última lectura aceptada de octubre. El historial muestra octubre como `PARCIAL`, con motivo `FALTA_LECTURA_FINAL`. |
| Incorporación de la lectura exacta de medianoche | Octubre pasa a `COMPLETO` y registra 200 litros. La misma lectura cierra octubre y abre noviembre. |
| Incorporación de una segunda lectura de noviembre con el mismo contador | El consumo de noviembre es cero. La fecha publicada corresponde a la medición, un segundo anterior a su recepción. |
| Consulta de estadísticas | Los tramos reproducen el consumo actual y el historial. La diferencia es −200 litros. La serie conserva septiembre sin datos y el resumen incluye solo octubre. |
| Fuentes externas en fallo durante la sesión | Las tres consultas responden HTTP 200 con datos locales. Los contadores de llamadas externas permanecen en los valores del inicio de sesión. |
| Cierre de sesión | Las tres rutas devuelven HTTP 401 al reutilizar el token. |

Las pruebas del bloque también cubren límites de consulta, selección de
un periodo anterior, lecturas fuera del periodo, intentos de consultar otro suministro,
cambios de medidor, retrocesos del contador y promedios que incluyen meses
completos de consumo cero. Los casos de tarifas, costos y proyecciones
corresponden al siguiente bloque.

## Comprobaciones realizadas en Windows

El 11 de noviembre de 2026, las tres rutas respondieron HTTP 200 con Nivora
conectado a MySQL 8.0.46. La prueba de consumo actual registró 1846 litros
hasta su lectura de corte. La consulta de historial devolvió octubre
completo con 5637 litros y septiembre y agosto con lecturas insuficientes.
La prueba posterior de estadísticas comparó 1895 litros del tramo medido
de noviembre con los 5637 litros de octubre, una diferencia de −3742
litros. Su resumen contó un mes completo y dos sin datos.

Los comandos de comprobación están en [consumo actual](consumo-actual.md),
[historial mensual](historial-mensual.md) y
[estadísticas de consumo](estadisticas-consumo.md).

## Repetir la prueba

En CMD, desde `tecnologia\nivora-backend`, ejecuta la suite completa.

```bat
mvnw.cmd clean verify
```

Para ejecutar únicamente el cambio de mes con las tres consultas, usa este comando.

```bat
mvnw.cmd -Dtest=AccesoConsultaIntegradoTests#consumoHistorialYEstadisticasCoincidenAlCambiarElMesDeLima test
```

La suite usa una base de pruebas H2 y proveedores HTTP ficticios.
El caso de cambio de mes también pasó con MySQL 8.0.46 en una base aislada.
