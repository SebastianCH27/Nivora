# Costos estimados en Flutter

Base: `main`, commit `e9786ecda6117338fc4f7686a6fe305d7217b48b`.
Se usa el endpoint existente del backend. No se agregan dependencias,
migraciones ni cambios en los servicios Java.

## Uso

Después de ingresar, abre **Costos estimados** junto a Consumo actual e
Historial mensual. La primera consulta se hace al abrir la pestaña.

Se muestran el mes en curso y 3 meses anteriores de forma predeterminada.
El selector permite cambiar a 6 o 12 meses anteriores, siempre además del
mes en curso. Los meses terminados aparecen del más reciente al más antiguo.
El mes de referencia se toma del reloj del backend en hora de Lima.

**Actualizar costos** vuelve a consultar el rango seleccionado. Pulsa un mes
para desplegar el desglose, la tarifa aplicada y el tramo medido.

## Presentación de los resultados

| Estado del backend | Qué muestra la app |
|---|---|
| `ESTIMADO` | Total estimado en soles y los conceptos que lo componen. |
| `CONCEPTOS_INCOMPLETOS` | Subtotal conocido, aviso de conceptos faltantes y total no disponible. |
| `SIN_CONSUMO_CALCULABLE` | Importe no disponible y estado de las lecturas. |
| `SIN_TARIFA_COMPATIBLE` | Consumo medido, importe no disponible y explicación de la tarifa faltante. |

La cobertura del consumo se muestra por separado del estado económico:
un costo con todos los conceptos puede corresponder a lecturas parciales.
La app lo identifica junto al importe y explica que puede faltar consumo.
Para el mes actual, `COMPLETO` significa cobertura hasta la última lectura;
no se presenta como un mes ya terminado.

El desglose muestra agua, alcantarillado, cargo fijo mensual, subtotal
conocido, IGV y total estimado. Un concepto ausente aparece como
**No disponible**. Un cero que el servidor informa aparece como **S/ 0.00**.
El cargo fijo corresponde al mes completo, incluso con consumo parcial.

Los valores y redondeos proceden del backend. La app conserva las cadenas
decimales y verifica su coherencia con enteros de precisión arbitraria.
No calcula nuevas tarifas ni modifica los importes recibidos. Se validan
el suministro, PEN, Lima, meses consecutivos, límites mensuales, cobertura,
tarifa aplicable y correspondencia entre subtotal, conceptos y total.

La tarjeta distingue el costo estimado del monto a pagar, que corresponde
al recibo emitido por la Empresa de Agua. Si faltan alcantarillado o IGV,
no convierte un subtotal en un total estimado.

## Sesión y errores

Se consulta `GET /api/v1/acceso/consulta/costos?meses=N` con el mismo Bearer
de la sesión de consulta. No se envía otro suministro ni credenciales de
APIsPERU desde la app. Se reutilizan el límite de 20 segundos, el rechazo de
redirecciones y el manejo de errores existentes.

Un fallo al actualizar conserva los costos anteriores del mismo rango,
con un aviso visible y un botón de reintento. Cambiar de filtro retira
los valores anteriores antes de consultar; un fallo del nuevo rango no
atribuye esos datos al filtro recién seleccionado.

El cierre de sesión, su vencimiento y un HTTP 401 desde cualquiera de
las consultas eliminan también los costos. Las respuestas pendientes de
una sesión anterior se descartan, incluso después de un nuevo ingreso.

## Comprobación en la PC

Desde `C:\Users\HP\Desktop\Nivora\tecnologia\nivora-frontend`, ejecuta
cada comando por separado:

```cmd
flutter pub get --enforce-lockfile
```

```cmd
flutter analyze
```

```cmd
flutter test
```

Las pruebas incluyen transporte HTTP local, estados, importes exactos,
ceros y conceptos ausentes, vigencias, filtros, errores, sesión, consultas
pendientes e interfaz a 320 y 1200 píxeles. Usan respuestas controladas;
la conexión a tus servicios se comprueba en los pasos siguientes.

Con MySQL, Empresa de Agua en 8081 y Nivora en 8082 disponibles:

1. Ingresa con el suministro y DNI habituales y abre **Costos estimados**.
2. Comprueba el mes actual y los 3 meses anteriores. Cambia a 6 y 12.
3. Abre octubre de 2026 si corresponde a los meses devueltos. La documentación
   del backend describe 5637 litros y un subtotal de S/ 13.46 con la tarifa
   de demostración. Es una referencia condicionada a conservar esas lecturas
   y esa revisión tarifaria; la pantalla muestra lo que devuelva tu backend.
4. Revisa el desglose y la tarifa. Si falta alcantarillado o IGV, debe aparecer
   **Subtotal conocido** y el total debe quedar sin importe.
5. Prueba actualizar. Si el backend se detiene después de consultar, debe
   mostrarse un error con los datos anteriores identificados como tales.
   Restablece el servicio y pulsa **Reintentar costos**.
6. Vuelve a Consumo actual e Historial mensual para comprobar su continuidad.
7. Cierra sesión desde Costos estimados y vuelve a ingresar. Los datos de
   la sesión anterior deben retirarse.

Para Windows, ejecuta `flutter run -d windows`. Para el S22, sigue los
pasos de compilación y ADB del [README](../README.md). En la laptop de
4 GB, compila el APK con los servicios Java detenidos y vuelve a iniciarlos
antes de usar la aplicación. El APK se ejecuta con `--use-application-binary`
después de habilitar el puente al puerto 8082.

El contrato completo y las reglas de cálculo están en
`tecnologia/nivora-backend/documentacion/costos-estimados.md`.
