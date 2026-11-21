# Comparación mensual en Flutter

Base: `main`, commit `e57a64ca428ce50c31773f73520d67a40437375f`.
Esta entrega integra el endpoint existente sin dependencias nuevas, migraciones
ni cambios en el backend. Conserva el diseño actual; la revisión visual general
queda para el final del desarrollo.

## Consulta y selección

Después de ingresar, abre **Comparación**. La primera consulta usa
`GET /api/v1/acceso/consulta/comparacion`, con el mismo token Bearer de la sesión.
El servidor elige el mes actual y el anterior según su calendario en Lima.
La app no obtiene el mes actual del reloj del teléfono.

Los selectores **Mes base** y **Mes comparado** ofrecen el mes actual y sus doce
anteriores. Cambiar un selector elimina el resultado anterior; **Comparar meses**
envía ambos periodos como `periodoBase=AAAA-MM` y `periodoComparado=AAAA-MM`.
Se puede invertir el orden o comparar un mes consigo mismo. Los dos meses pueden
estar separados como máximo doce meses. La API admite otros pares válidos; esta
pantalla ofrece una ventana de trece meses para simplificar la selección.

**Actual y anterior** consulta de nuevo la selección predeterminada del servidor.
Sirve también para actualizar los meses disponibles después de un cambio de mes.
Cada periodo conserva sus límites reales; se admite un calendario configurado
para comenzar en un día distinto al primero.

## Interpretación

La diferencia siempre es **mes comparado menos mes base**:

- Un signo positivo indica mayor volumen o importe medido en el comparado.
- Un signo negativo indica menor volumen o importe.
- Cero representa una diferencia calculada de cero. La ausencia de datos
  se muestra con una explicación, sin convertirla en cero.

Se conservan los decimales exactos del backend como texto y se comprueba su
coherencia con enteros. El frontend no recalcula tarifas, impuestos ni pronósticos.
Por ejemplo, 13200 litros menos 10000 litros se presentan como **+3,200 m³**
y **+3200 litros**. Es un ejemplo de prueba, no un dato de la aplicación.

| Estado del backend | Presentación |
|---|---|
| `COMPLETA` | Ambos periodos están cerrados, con cobertura completa y totales estimados disponibles. |
| `PARCIAL` | Hay diferencias calculables, pero algún periodo sigue abierto o tiene cobertura parcial. |
| `NO_CALCULABLE` | Se explica la falta de lecturas o totales. La diferencia de volumen sigue visible si ambos volúmenes son calculables. |

La diferencia de costo solo se muestra cuando existen **ambos totales estimados**.
Los subtotales con conceptos faltantes se conservan en el detalle de cada mes.
Si la tarifa local no informa alcantarillado o IGV, es esperable poder comparar
el volumen y ver el costo como no disponible.

La pantalla avisa cuando un mes sigue en curso, las mediciones abarcan tiempos
distintos o las tarifas son diferentes. El detalle muestra los límites del
periodo, el tiempo entre mediciones, la cobertura y la tarjeta desplegable de
costos con sus conceptos y tarifa.

El tiempo entre mediciones no garantiza cobertura continua: debe leerse junto
con el estado de cobertura. No se igualan los meses por número de días ni se
proyecta el mes abierto. El importe a pagar corresponde al recibo emitido.

## Errores y sesión

Un fallo inicial permite reintentar. Un fallo al repetir los mismos filtros
conserva la última comparación identificada como tal. Al cambiar de filtros no
se presentan datos de los meses anteriores. Los selectores se desactivan durante
la consulta; las otras pestañas mantienen sus datos y no se añade una consulta
al inicio de sesión.

El vencimiento, el cierre de sesión o un HTTP 401 desde cualquier consulta borran
también la comparación y sus filtros. Se descartan respuestas pendientes de
sesiones anteriores. Se reutilizan el límite de espera y los mensajes seguros
del cliente HTTP existente.

## Comprobación automatizada

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

Se comprobaron **238 pruebas** del frontend: las 174 anteriores y 64 nuevas.
Cubren HTTP local, selección explícita y predeterminada, diferencias positivas,
negativas y cero, estados incompletos, coherencia de respuestas, calendario
mensual, sesión y resultados tardíos. Las pruebas de widgets comprueban anchos
de 320 y 1200 píxeles, cambios de filtros y detalle de costos.

## Recorrido en la PC y el S22

1. Compila con `flutter build apk --debug`, con los servicios Java detenidos
   para reducir el uso simultáneo de RAM.
2. Inicia MySQL, Empresa de Agua en 8081 y Nivora en 8082 con su configuración
   habitual. Conecta el S22 y abre el APK siguiendo el
   [README](../README.md#probar-en-el-samsung-galaxy-s22).
3. Ingresa y abre **Comparación**. Comprueba los meses, la fecha de consulta
   y los avisos correspondientes a las lecturas reales.
4. Elige dos meses cerrados y pulsa **Comparar meses**. Abre el detalle de cada
   mes y revisa cobertura, conceptos disponibles y tarifa.
5. Invierte los mismos meses y comprueba que se inviertan los signos de las
   diferencias disponibles. Prueba también el mismo mes en ambos selectores.
6. Incluye el mes en curso y revisa el aviso. Si faltan conceptos tarifarios,
   el costo no debe mostrarse como cero ni como diferencia de subtotales.
7. Pulsa **Actual y anterior**. Comprueba el resto de pestañas.
8. Si detienes el backend tras una consulta, repite **Comparar meses** y revisa
   el error con los últimos datos identificados. Restablece el servicio y
   pulsa **Reintentar comparación**.
9. Cierra sesión y verifica el regreso al formulario.

En Windows, con los servicios disponibles, ejecuta `flutter run -d windows`.
La compilación nativa de Android/Windows y la prueba contra tus servicios reales
se completan en tu PC; las pruebas automatizadas emplean respuestas controladas.

Contrato y reglas del backend:
`tecnologia/nivora-backend/documentacion/comparacion-costos.md`.
