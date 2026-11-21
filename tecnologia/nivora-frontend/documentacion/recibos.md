# Recibos emitidos en Flutter

Base: `main`, commit `80d76b4ed3ee113745d40f8a540fbc19921f2fc0`.
Se integra el endpoint existente sin cambios de backend, base de datos ni
dependencias. El diseño general se revisará al terminar las funcionalidades.
Se ajustó el encabezado de marca para que quepa con letra ampliada en pantallas
estrechas; los importes y el contenido conservan el tamaño de texto elegido.

## Funcionamiento

Después de ingresar, abre **Recibos**. La consulta usa el mismo token Bearer y
el suministro de la sesión. La app solicita
`GET /api/v1/acceso/consulta/recibos?limite=12` y presenta los recibos guardados
en Nivora, del periodo más reciente al más antiguo. No añade peticiones al
inicio de sesión ni al regresar a una pestaña que ya tiene datos.

**Actualizar recibos** repite la consulta de la página visible.
**Ver recibos anteriores** envía el `siguienteAntesDe` recibido como `antesDe`;
el mes de corte es exclusivo. **Volver a los más recientes** elimina ese corte.
Se conserva una sola página de hasta doce recibos para limitar la memoria usada.
La API admite límites de 1 a 60, aunque esta pantalla utiliza doce.

## Información del recibo

Cada tarjeta muestra:

- Mes facturado y número de recibo, conservando ceros iniciales.
- **Importe del recibo**, el total definitivo emitido por la empresa de agua.
- Consumo facturado en litros y m³ y fecha de vencimiento.

El detalle desplegable incluye cargo fijo, importe por consumo, importe total,
fecha de disponibilidad, código y versión de tarifa, periodo facturado y fecha
de primera recepción en Nivora. El endpoint entrega estos dos cargos y su suma;
el frontend no inventa una separación adicional de impuestos u otros conceptos.

Los valores monetarios se conservan como cadenas decimales. Se comprueba la suma
de los cargos con enteros, sin volver a calcular tarifas. La conversión de litros
a m³ es exacta y conserva las fracciones de litro. Un importe o consumo cero
continúa siendo un valor válido.

Los recibos emitidos pueden tener un total disponible aunque la estimación de
Nivora tenga conceptos pendientes: son fuentes y resultados diferentes. La app
presenta el importe recibido del endpoint de recibos.

Las fechas de disponibilidad y vencimiento son fechas civiles de Lima: no se
les resta una zona horaria. El instante `recibidoEn` sí se convierte desde UTC
a Lima y se identifica como **Recibido en Nivora**. No es la hora de la consulta.

El backend informa el periodo civil facturado por la empresa y el periodo de
análisis de Nivora. Cuando sus límites difieren, el detalle muestra ambos y
avisa que deben revisarse antes de comparar consumos. El frontend conserva
el calendario de análisis que recibe del servidor.

La consulta no incluye el estado de pago ni un documento PDF. Esta entrega
presenta los datos emitidos; no clasifica un recibo como pagado o impagado por
su fecha de vencimiento ni incorpora acciones de pago.

## Errores y sesión

Una lista vacía se explica como ausencia de recibos guardados, sin generar
recibos ni importes de ejemplo. Cuando falla una actualización del mismo rango,
se conserva la última página identificada como tal. Al cambiar de rango se
eliminan los datos anteriores para no atribuirlos al corte nuevo. Se permite
reintentar el rango que falló o volver a los más recientes.

Mientras se consulta se desactivan las acciones de recibos. Se conservan los
límites de espera, el rechazo de redirecciones y los mensajes seguros del
cliente existente. Cerrar sesión, su vencimiento o cualquier HTTP 401 borra
también los recibos y el corte seleccionado. Se descartan respuestas tardías
de sesiones anteriores, incluso si ya hubo un nuevo ingreso.

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

Se comprobaron **313 pruebas** del frontend: las 238 anteriores y 75 nuevas.
Las pruebas cubren transporte HTTP local, parámetros, validación
del suministro y moneda, importes y unidades exactos, fechas civiles, ventanas
de análisis distintas, paginación, lista vacía, errores, sesiones y respuestas
pendientes. Las pruebas de widgets comprueban anchos de 320 y 1200 píxeles,
incluido un caso a 320 con letra ampliada y referencias largas.

## Recorrido en la PC y el S22

1. Con los servicios Java detenidos, compila `flutter build apk --debug` para
   reducir el uso simultáneo de RAM en la laptop.
2. Inicia MySQL, Empresa de Agua en 8081 y Nivora en 8082 con la configuración
   habitual. Conecta el S22 y abre el APK siguiendo el
   [README](../README.md#probar-en-el-samsung-galaxy-s22).
3. Ingresa y abre **Recibos**. Revisa número, mes, consumo, importe y vencimiento
   del recibo que esté guardado para tu suministro. Una lista vacía es válida
   si aún no hay recibos incorporados para ese suministro.
4. Despliega una tarjeta y comprueba los cargos y su suma, las fechas, la tarifa
   y los límites del periodo. Si el calendario de análisis es distinto,
   deben aparecer el aviso y ambas ventanas.
5. Si hay más de doce recibos, prueba **Ver recibos anteriores** y luego
   **Volver a los más recientes**. Con doce o menos no se necesita otra página.
6. Pulsa **Actualizar recibos**. Para comprobar un error, detén Nivora tras una
   consulta y repite: debe conservar la última página con un aviso. Restablece
   el backend y pulsa **Reintentar recibos**.
7. Revisa las otras pestañas y cierra sesión. Debe regresar al formulario sin
   conservar los datos del suministro anterior.

En Windows, con los servicios disponibles, ejecuta `flutter run -d windows`.
Las pruebas automatizadas usan respuestas controladas y no sustituyen la
compilación nativa ni la comprobación con los servicios reales de tu PC.

Contrato del backend:
`tecnologia/nivora-backend/documentacion/consulta-recibos.md`.
