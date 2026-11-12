# Verificación integrada de recibos

El recorrido cubre RF27 y RF28. Empresa de Agua emite el recibo a partir
del consumo mensual cerrado. Nivora lo incorpora mediante la tarea
programada y lo devuelve desde MySQL a la sesión de su suministro.

## Datos de prueba

Se usa el suministro ficticio `000101` con tarifa `DOMESTICA` v1, un
cargo fijo de S/ 5,00 y un precio de S/ 1,50 por metro cúbico.

| Periodo | Consumo | Importe definitivo | Disponible desde | Vencimiento |
|---|---:|---:|---|---|
| Septiembre de 2026 | 2000 L | S/ 8,00 | 2026-10-01 | 2026-10-08 |
| Octubre de 2026 | 5637 L | S/ 13,46 | 2026-11-01 | 2026-11-08 |

Para octubre, 5,637 m³ multiplicados por S/ 1,50 dan S/ 8,4555, que
se redondean a S/ 8,46 antes de sumar el cargo fijo. Empresa de Agua
publica S/ 13,46. La consulta de Nivora devuelve el mismo importe
junto al número y las fechas del recibo.

## Caso automatizado

`recibosEmitidosSeSincronizanYSePuedenConsultarDuranteUnaCaida` usa una
fuente HTTP controlada y ejecuta la sincronización, el almacenamiento y la
consulta autenticada de Nivora. El reloj de prueba permite avanzar hasta
el siguiente intento sin esperar los intervalos reales.

| Paso | Resultado esperado |
|---|---|
| Consulta antes de incorporar los recibos | HTTP 200 con arreglo vacío. |
| Primera sincronización | Dos recibos nuevos y cero repetidos. |
| Consulta con `limite=1` | Devuelve octubre y un cursor para pedir septiembre. |
| Fallo HTTP de Empresa de Agua | Registra `ERROR_FUENTE` y programa un reintento. La sesión vigente sigue consultando el recibo guardado. |
| Recuperación de la fuente | Cero nuevos y dos repetidos. Se conservan los importes, fechas y primera recepción. |
| Cierre de la sesión | La consulta posterior con el mismo token devuelve HTTP 401. |

## Comprobación con ambos servicios

Con MySQL 8.0.46 aislado y una identidad de prueba, se cargaron los
consumos cerrados de septiembre y octubre en Empresa de Agua. Su
tarea de emisión creó ambos recibos. La sincronización automática de
Nivora guardó los dos y la consulta autenticada los devolvió en dos
páginas, con los números, importes y vencimientos de la fuente.

Al detener Empresa de Agua, la sincronización registró `ERROR_FUENTE`
y ambas páginas siguieron respondiendo HTTP 200. Al volver a arrancar
la empresa, Nivora registró cero nuevos y dos repetidos. Ambas páginas
conservaron sus datos. El cierre de sesión devolvió HTTP 204 y una
consulta posterior con el token cerrado devolvió HTTP 401.

## Repetir en Windows

Desde CMD en `tecnologia\nivora-backend`, ejecuta la suite completa.

```bat
mvnw.cmd clean verify
```

Para ejecutar únicamente el caso integrado, usa esta línea.

```bat
mvnw.cmd -Dtest=AccesoConsultaIntegradoTests#recibosEmitidosSeSincronizanYSePuedenConsultarDuranteUnaCaida test
```

La prueba usa una base aislada. La consulta manual del recibo guardado se
puede repetir con [Consulta de recibos](consulta-recibos.md).
