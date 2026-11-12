# Verificación del bloque 5 de tarifas, costos y proyecciones

El caso `tarifasImportadasYPublicadasSeAplicanAlCambiarDeMesEnLima`
importa un historial por HTTP desde una Empresa de Agua de prueba,
incorpora lecturas acumuladas y publica una vigencia desde la API
administrativa. Las consultas de costo y proyección usan sesiones
de consulta y un reloj controlado para noviembre y diciembre de 2026.

| Momento | Volumen | Tarifa aplicada | Total estimado |
|---|---:|---|---:|
| Acumulado de noviembre al día 11 | 4000 L | Importada `DOMESTICA` v1 | S/ 15,34 |
| Proyección de noviembre con 10 días medidos | 12 000 L | Importada `DOMESTICA` v1 | S/ 36,58 |
| Noviembre cerrado en el historial de diciembre | 10 000 L | Importada `DOMESTICA` v1 | S/ 29,50 |
| Acumulado de diciembre al día 11 | 2000 L | Publicación administrativa, revisión 1 | S/ 10,00 |
| Proyección de diciembre con 10 días medidos | 6200 L | Publicación administrativa, revisión 1 | S/ 20,50 |

Las tarifas de la tabla son datos de prueba. Noviembre aplica S/ 1,50
de agua y S/ 0,50 de alcantarillado por m³ en el primer tramo, cargo fijo
de S/ 5,00 e IGV de 18 %. Por ejemplo, para 12 m³, los primeros 10 m³
suman S/ 20,00 y los 2 m³ siguientes suman S/ 6,00. Con el cargo fijo,
la base es S/ 31,00 y el IGV es S/ 5,58. Diciembre proyecta 2000 L en
10 días a 6200 L en 31 días. A S/ 2,50 por m³ entre agua y alcantarillado,
más S/ 5,00 de cargo fijo y con IGV definido en cero, el total es S/ 20,50.

La lectura de las 00:00 del 1 de diciembre en Lima cierra noviembre y
abre diciembre. La prueba comprueba que ambos costos referencian el
mismo identificador en ese límite. La proyección identifica la última
medición, la fecha de cálculo y el supuesto de ritmo promedio constante.
La actualización de Empresa de Agua conserva la tarifa anterior para
noviembre y registra una segunda revisión importada. Diciembre conserva
la publicación administrativa. Tras un fallo HTTP en la importación,
las consultas responden con las revisiones guardadas y los contadores
confirman que las consultas se resuelven localmente.

Los casos complementarios cubren los periodos sin una tarifa que los
cubra por completo, el costo con conceptos faltantes, el mínimo de
24 horas de observación y la consulta de revisiones administrativas
anteriores.

## Comprobaciones realizadas en Windows

Las rutas respondieron HTTP 200 con MySQL 8.0.46. Con la tarifa importada
`DOMESTICA` v1, el costo de 1949 L dio un subtotal de S/ 7,92 y la
proyección de 5382 L dio un subtotal de S/ 13,07. En ambos casos, el
total quedó indefinido porque la tarifa de demostración solo informaba
agua y cargo fijo. La consulta administrativa mostró revisión importada 1
y revisión administrativa 0.

## Repetir la prueba

Desde CMD, en `tecnologia\nivora-backend`, ejecuta la suite completa.

```bat
mvnw.cmd clean verify
```

Para ejecutar solo el caso de cambio de vigencia, usa esta línea.

```bat
mvnw.cmd -Dtest=AccesoConsultaIntegradoTests#tarifasImportadasYPublicadasSeAplicanAlCambiarDeMesEnLima test
```

La suite usa H2, un proveedor de tarifas de prueba y un servicio de
identidad simulado. El mismo caso se verificó con MySQL 8.0.46 en una
base aislada.
