# Proyección mensual en Flutter

Base: `main`, commit `a2f75efd141494a289c58b6d1ef25655859557b8`.
Se integra el endpoint existente sin agregar dependencias ni migraciones.
La revisión visual general queda para el final del desarrollo; esta entrega
mantiene el estilo actual y comprueba legibilidad y adaptación al ancho.

## Funcionamiento

Después de ingresar, abre **Proyección mensual**. La consulta se hace al abrir
la pestaña y **Actualizar proyección** permite repetirla. La proyección no
añade una consulta al inicio de sesión. Las otras pestañas conservan sus datos.

La respuesta de `GET /api/v1/acceso/consulta/proyeccion` utiliza el suministro
de la sesión y su token Bearer. El mes y los límites se toman del backend en
hora de Lima. La app muestra los valores recibidos y no vuelve a calcular
el pronóstico, el promedio, la tarifa ni los impuestos.

La pantalla separa:

- **Consumo observado**: volumen medido y tramo de lecturas utilizado.
- **Consumo proyectado al cierre**: litros y m³ que se estiman al terminar
  el mes, manteniendo el ritmo promedio observado.
- **Costo proyectado**: total estimado, subtotal conocido o ausencia de una
  tarifa compatible. Puede desplegarse el desglose y la tarifa aplicada.
- Fecha de cálculo, última lectura y estado de actualización de las mediciones.

El backend requiere cobertura continua desde el inicio del mes y al menos
24 horas de observación. Por ejemplo, 4000 litros en los primeros 10 días
de un mes de 30 días producen una proyección de 12000 litros. Esta es una
referencia de prueba, no un dato insertado en la aplicación.

## Estados

| Estado del backend | Qué muestra la pantalla |
|---|---|
| `PROYECTADO` | Volumen al cierre, promedio observado y costo proyectado disponible. |
| `SIN_CONSUMO_CALCULABLE` | Explicación de las lecturas insuficientes o incompatibles. |
| `COBERTURA_INCOMPLETA` | Aviso de que faltan lecturas continuas desde el inicio del mes. |
| `TIEMPO_INSUFICIENTE` | Aviso de que hace falta al menos un día de observación. |
| `PERIODO_CERRADO` | Aviso de cierre del periodo y referencia al historial. |

Los casos sin proyección no se muestran como un consumo o costo cero.
Si el volumen está proyectado y falta una tarifa, el volumen sigue visible
y se explica por qué el costo no está disponible.

Cuando faltan conceptos tarifarios, se muestra **Subtotal proyectado conocido**
y el total queda sin importe. Un cero informado conserva su significado.
El desglose reutiliza la validación de los costos estimados, sin convertir
importes a `double`. El monto a pagar corresponde al recibo emitido por la
empresa de agua y no a esta proyección.

## Conexión y sesión

Se reutilizan los límites de espera, el rechazo de redirecciones y los errores
de la API existente. Un fallo inicial permite reintentar. Un fallo al actualizar
conserva la proyección anterior con un aviso de que son los últimos datos
consultados. Cerrar sesión, su vencimiento o un HTTP 401 desde cualquiera de
las consultas eliminan también la proyección. Se descartan respuestas tardías
de una sesión anterior, incluso después de un nuevo ingreso.

## Verificar en la PC

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

Las pruebas usan respuestas controladas y transporte HTTP local. Cubren los
estados, unidades exactas, duraciones con fracciones de segundo, importes,
sesión, respuestas pendientes y pantallas de 320 y 1200 píxeles.

## Verificar con los servicios y el S22

Para reducir el uso de RAM en la laptop, compila primero el APK con los
servicios Java detenidos:

```cmd
flutter build apk --debug
```

Después inicia MySQL, Empresa de Agua en 8081 y Nivora en 8082 con la
configuración habitual. Identifica el S22 y crea el puente al puerto 8082
siguiendo el [README](../README.md#probar-en-el-samsung-galaxy-s22). Abre el APK
con `--use-application-binary`, utilizando el identificador actual del teléfono.

1. Ingresa con tu suministro y DNI habituales.
2. Abre **Proyección mensual** y revisa el mes y la fecha del cálculo.
3. Comprueba que el consumo observado se identifique por separado del volumen
   al cierre. La proyección depende de las lecturas guardadas en tu backend.
4. Si hay costo proyectado, abre el detalle. Comprueba el subtotal o total y
   los conceptos que tengan importe disponible.
5. Si faltan lecturas, cobertura o tiempo, comprueba el aviso correspondiente.
   No se necesitan datos ficticios para llenar la pantalla.
6. Prueba **Actualizar proyección**. Si detienes el backend tras una consulta,
   debe conservar los datos previos con un aviso. Restablece el servicio y
   prueba **Reintentar proyección**.
7. Revisa Consumo actual, Historial mensual y Costos estimados.
8. Cierra sesión y verifica que vuelva el formulario sin los datos anteriores.

Para probar en Windows, con los servicios disponibles, ejecuta
`flutter run -d windows`. Las pruebas automatizadas no sustituyen estos
recorridos contra los servicios reales de tu PC.

Las reglas completas de cálculo están en la documentación del backend:
`tecnologia/nivora-backend/documentacion/proyeccion-mensual.md`.
