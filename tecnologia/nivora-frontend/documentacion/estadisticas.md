# Estadísticas y gráfico mensual en Flutter

Base: `main`, commit `4fb56cb653dca1112ae143f48fbb7417a9d45571`.
Se integra el endpoint existente sin dependencias nuevas, cambios de backend ni
migraciones. El diseño general queda para el final; se mantiene el estilo actual.

## Consulta y rango

Abre **Estadísticas** después de ingresar. La app consulta
`GET /api/v1/acceso/consulta/estadisticas?meses=12` con el Bearer de la sesión.
El suministro procede de esa sesión. El selector ofrece 3, 6 o 12 meses y
**Actualizar estadísticas** vuelve a consultar el mismo rango.

Los meses, sus límites y la fecha de consulta proceden del backend. El gráfico
contiene los meses anteriores, en orden cronológico, y excluye el mes en curso.
Se admite el calendario mensual configurado en Nivora, incluidos los días de
corte distintos al primero y su ajuste a la duración del mes.

La respuesta también incluye una comparación del mes en curso con el anterior;
se comprueba su coherencia, pero esta pantalla se centra en la serie y su resumen.
La elección de dos meses y sus costos permanece en **Comparación**.

## Resumen

Se muestran los conteos de meses completos, parciales, sin datos y con lecturas
en revisión. Total, promedio, mínimo y máximo utilizan únicamente los meses
que el backend identifica con cobertura completa.

Un mes completo con consumo cero participa en el promedio. Los meses parciales
conservan su volumen en el gráfico, aunque no participan en los cuatro valores
del resumen. Si no hay meses completos, el resumen se explica como no disponible.
No se convierte su ausencia en cero.

El promedio llega redondeado por el backend a tres decimales de litro con
`HALF_UP`; su equivalente en m³ puede requerir seis decimales. Las cantidades
se conservan como texto y se comprueba su coherencia con enteros. El frontend
muestra los resultados del servidor sin estimar lecturas ni volver a calcular
tarifas o proyecciones.

## Gráfico de barras

La gráfica de columnas verticales aparece antes del resumen. Los meses están
en el eje horizontal y el consumo en m³ en el eje vertical, con líneas de
referencia y una escala común desde cero. El límite superior se ajusta para
incluir el mayor volumen calculable, incluidos los parciales.

El valor exacto aparece sobre cada columna. Las etiquetas inferiores muestran
el mes y el año. En pantallas estrechas, los rangos largos se desplazan a los
lados para mantener la legibilidad; inicialmente se muestran los más recientes.

| Estado | Presentación |
|---|---|
| `COMPLETO` | Columna turquesa y estado Mes completo en el detalle. |
| `PARCIAL` | Columna de otro color y estado Consumo parcial en el detalle. |
| `SIN_DATOS` | Marcador S/D, sin columna. |
| `INCOMPATIBLE` | Marcador Rev., sin columna. |

Un volumen calculado de cero conserva su etiqueta numérica y una marca en el
origen. Si todos son cero, se explica ese resultado sin dividir por una escala
cero. Si no hay volúmenes calculables, se conservan los meses con sus estados.
El estado se identifica mediante texto, además del color.

La altura de una columna es una representación visual; los textos mantienen los
valores exactos. La proporción se obtiene con enteros antes de convertir un
valor acotado a coordenadas de pantalla, evitando perder unidades en los datos.
El gráfico usa widgets de Flutter y no agrega una biblioteca de gráficos.

Al tocar una columna o su mes se selecciona el detalle situado debajo del
gráfico. Esa tarjeta muestra el volumen y la cobertura; al desplegarla aparecen
los límites del periodo y el tramo de lecturas. Los parciales explican la
cobertura faltante. Si existen huecos entre intervalos, se avisa que el tramo
no equivale a cobertura continua.

La presentación elimina ceros decimales finales sin redondear. Por ejemplo,
el promedio `5.637000` m³ se muestra como `5,637 m³`, mientras que un promedio
de `0.000333` m³ conserva sus seis decimales. Cada columna ofrece al lector
de pantalla el mes, la cobertura, las cantidades y una acción de selección.

## Conexión y sesión

La carga es a demanda y se conservan los datos de las otras pestañas. Durante
una consulta se desactivan los controles de esta pantalla. Al cambiar el rango
se eliminan los resultados anteriores para no atribuirlos a la selección nueva.

Si falla una actualización del mismo rango, se conservan los últimos datos con
un aviso. **Reintentar estadísticas** repite el rango que falló. El cliente
mantiene sus límites de espera, mensajes seguros y rechazo de redirecciones.
El cierre de sesión, vencimiento o cualquier HTTP 401 borra también las
estadísticas. Las respuestas tardías de sesiones anteriores se descartan.

## Verificación en la PC

Desde `tecnologia/nivora-frontend`, ejecuta cada comando por separado:

```cmd
flutter pub get --enforce-lockfile
```

```cmd
flutter analyze
```

```cmd
flutter test
```

Se comprobaron **381 pruebas** del frontend: las 313 anteriores y 68 nuevas.
Cubren transporte HTTP local, filtros, estados, unidades y promedios exactos,
calendario mensual, coherencia de la respuesta, sesión y resultados pendientes.
Las pruebas de widgets comprueban la escala común, columnas verticales, ceros,
datos ausentes, selección accesible y desplazamiento entre meses. Se comprueban
anchos de 320 y 1200 píxeles, también con letra ampliada y cantidades grandes.

## Recorrido en el S22 y Windows

1. Compila `flutter build apk --debug` con los servicios Java detenidos para
   reducir el uso simultáneo de RAM.
2. Inicia MySQL, Empresa de Agua en 8081 y Nivora en 8082 con su configuración
   habitual. Instala el APK y abre Nivora siguiendo el
   [README](../README.md#probar-en-el-samsung-galaxy-s22). Se usa ADB directamente
   para evitar la espera de conexión del depurador observada en la laptop.
3. Ingresa y abre **Estadísticas**. Comprueba el rango de meses, la fecha de
   consulta y los conteos de cobertura.
4. Revisa los meses completos y sus valores del resumen. Si el suministro
   solo tiene un mes completo, total, promedio, mínimo y máximo pueden coincidir.
5. Cambia a 3 y 6 meses. Comprueba que cambien las filas del gráfico y el rango,
   conservando los estados reales de las lecturas guardadas.
6. Toca una columna o un mes y despliega su tarjeta de detalle para revisar
   sus límites y el tramo medido. Los meses sin
   datos deben conservar su explicación y no presentarse como un consumo cero.
7. Prueba **Actualizar estadísticas**, revisa las otras pestañas y cierra sesión.

En Windows, con los servicios disponibles, ejecuta `flutter run -d windows`.
Las pruebas automatizadas usan respuestas controladas. La compilación nativa
y la comprobación contra los servicios reales se completan en tu PC.

Contrato del backend:
`tecnologia/nivora-backend/documentacion/estadisticas-consumo.md`.
