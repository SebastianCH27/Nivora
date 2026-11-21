# Nivora: aplicación para Android y Windows

Frontend con acceso mediante código de suministro y DNI, consumo actual,
historial mensual, costos estimados, proyección mensual, comparación de meses,
estadísticas con gráfico mensual, recibos,
actualización manual y cierre de sesión. Usa el contrato
existente de `nivora-backend`; no contiene un acceso de demostración ni datos simulados
en la aplicación.

## Ejecutar las comprobaciones

Requiere Flutter 3.50.0 y Dart 3.14.0. Desde esta carpeta, ejecuta cada comando por separado:

```cmd
flutter pub get --enforce-lockfile
```

```cmd
flutter analyze
```

```cmd
flutter test
```

## Probar en el Samsung Galaxy S22

En un equipo de 4 GB, cierra el emulador y Android Studio. Compila antes de iniciar los
servicios Java para reducir el uso simultáneo de memoria. La configuración de Gradle
conserva un máximo de 1 GB de heap y un trabajador. Evita abrir todo el repositorio en
VS Code: abre solamente `tecnologia/nivora-frontend`.

1. Conecta el teléfono mediante la depuración inalámbrica ya emparejada. La dirección
   IP y el puerto se consultan en **Depuración inalámbrica**, en la pantalla principal;
   el puerto de emparejamiento es distinto y no sirve para `adb connect`.
2. Compila el APK actualizado, con los servicios Java detenidos:

```cmd
flutter build apk --debug
```

3. Inicia `empresa-agua` en el puerto 8081 y `nivora-backend` en el puerto 8082 con su
   configuración habitual. MySQL debe estar disponible y la integración de identidad
   del backend configurada. El suministro debe existir y el DNI debe ser válido para
   la fuente de identidad. No introduzcas tokens de APIs externas en el frontend.
4. Conecta el teléfono; sustituye la dirección si cambió:

```cmd
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" connect 192.168.18.41:44219
```

5. Habilita el acceso desde el teléfono al backend local:

```cmd
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" -s 192.168.18.41:44219 reverse tcp:8082 tcp:8082
```

6. Instala el APK que acabas de compilar, sin repetir Gradle:

```cmd
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" -s 192.168.18.41:44219 install -r "build\app\outputs\flutter-apk\app-debug.apk"
```

7. Abre Nivora directamente:

```cmd
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" -s 192.168.18.41:44219 shell am start -S -n pe.nivora.nivora_frontend/.MainActivity
```

Esta ruta permite comprobar la app sin esperar la conexión del servicio de
depuración de Flutter. El reinicio de la app requiere volver a ingresar.

La aplicación usa `http://127.0.0.1:8082` durante el desarrollo. `adb reverse` conecta
ese puerto del teléfono con el mismo puerto de la PC; también funciona por ADB
inalámbrico. Repite el paso del puente si reinicias o reconectas el teléfono.

Para trabajar con recarga de cambios cuando los servicios ya estén iniciados y haya
memoria disponible, el siguiente ayudante conecta, comprueba ADB, crea el puente y
realiza `flutter run`:

```cmd
powershell -NoProfile -ExecutionPolicy Bypass -File herramientas\IniciarCelular.ps1 -Dispositivo 192.168.18.41:44219
```

`Bypass` se aplica solamente a ese proceso de PowerShell. Para un dispositivo USB
se puede pasar su identificador en lugar de la dirección IP.

## Probar en Windows

Con el backend local disponible:

```cmd
flutter run -d windows
```

La interfaz se adapta al ancho de la ventana. El código de suministro se mantiene
como texto para conservar sus ceros iniciales. El DNI debe tener ocho dígitos.

## Comportamiento y contrato

- `POST /api/v1/acceso/consulta/sesiones`: crea la sesión con suministro y DNI.
- `GET /api/v1/acceso/consulta/consumo-actual`: consulta con el Bearer de la sesión.
- `GET /api/v1/acceso/consulta/historial?meses=12`: historial con el mismo Bearer.
  Se carga al abrir **Historial mensual**, permite elegir 3, 6 o 12 meses,
  recorrer ventanas anteriores y regresar a los últimos meses. `hasta=AAAA-MM`
  se envía al navegar; el primer rango lo decide el backend en hora de Lima.
- `DELETE /api/v1/acceso/consulta/sesion`: revoca la sesión al salir.
- `GET /api/v1/acceso/consulta/costos?meses=3`: costo del mes actual y de
  3, 6 o 12 meses anteriores. Se consulta al abrir **Costos estimados**.
  Cada mes muestra un total estimado, un subtotal conocido cuando faltan
  conceptos, o el motivo que impide estimar. El detalle conserva las unidades,
  la cobertura, los conceptos disponibles y la tarifa aplicada.
- `GET /api/v1/acceso/consulta/proyeccion`: proyección del consumo y del costo
  del mes actual, usando el mismo Bearer. Se carga al abrir **Proyección mensual**.
  Distingue lo observado, el volumen proyectado y el total o subtotal proyectado.
  Explica cuándo faltan lecturas, cobertura o tiempo suficiente para proyectar.
- `GET /api/v1/acceso/consulta/comparacion`: compara el consumo y el costo
  estimado de dos meses. Se carga al abrir **Comparación**; inicialmente el
  backend elige el actual y el anterior. Los selectores permiten comparar
  dos meses entre el actual y sus doce anteriores, enviando `periodoBase`
  y `periodoComparado`. Muestra diferencias con signo, cobertura y avisos
  sobre periodos en curso, duraciones y tarifas diferentes. Cuando faltan
  totales, puede conservar la diferencia de consumo sin inventar un costo.
- `GET /api/v1/acceso/consulta/recibos?limite=12`: recibos emitidos guardados
  para el suministro de la sesión. Se carga al abrir **Recibos**. Cada tarjeta
  muestra número, periodo, consumo facturado, importe definitivo y vencimiento.
  Al desplegarla aparecen los cargos, la disponibilidad, la tarifa, la fecha
  de recepción y los límites del periodo facturado. Se avisa cuando difiere
  del periodo de análisis de Nivora. Permite consultar recibos anteriores con
  `antesDe=AAAA-MM` y volver a los más recientes.
- `GET /api/v1/acceso/consulta/estadisticas?meses=12`: serie cronológica de
  meses anteriores con un resumen de los que tienen cobertura completa.
  Se carga al abrir **Estadísticas** y permite elegir 3, 6 o 12 meses.
  Presenta un gráfico de columnas verticales con meses abajo y escala común
  de consumo en m³ al costado, seguido de total, promedio, mínimo y máximo.
  Tocar una columna selecciona su detalle de cobertura y fechas. Los parciales
  conservan su volumen; los meses sin datos se distinguen de los ceros.
- El token se mantiene únicamente en memoria. El formulario con el DNI se destruye
  al ingresar. El cierre de la aplicación no conserva el acceso.
- Al vencer la sesión o recibir 401 en una consulta, se borran los datos y vuelve
  el formulario. Se comprueba el vencimiento también al regresar a la aplicación.
- 400, 401, 429, 503, desconexiones, respuestas inválidas y espera agotada tienen
  mensajes visibles. `Retry-After` impide repetir el acceso antes de tiempo.
- Si falla una actualización, se identifica el resultado anterior como tal.
  En historial solo se conserva el resultado si pertenece al mismo filtro.
  Cambiar de rango elimina los meses anteriores mientras se consulta el nuevo.
- Si falla el cierre remoto, se elimina la sesión local y se informa que no se
  pudo confirmar la revocación; el servidor conserva su vencimiento habitual.
- Los valores de consumo se conservan como cadenas decimales exactas. `SIN_DATOS`
  e `INCOMPATIBLE` no se representan como cero. Se indica la cobertura parcial,
  el tramo medido y la última lectura; las fechas se muestran en hora de Lima.
- Las peticiones tienen un límite total de 20 segundos y no siguen redirecciones.

Los detalles de cobertura, navegación y comprobación del historial están en
[Historial mensual](documentacion/historial-mensual.md).
La consulta en soles, sus estados y las pruebas manuales se describen en
[Costos estimados](documentacion/costos-estimados.md).
Los supuestos, estados y comprobaciones de la nueva pestaña están en
[Proyección mensual](documentacion/proyeccion-mensual.md).
La selección de periodos y la lectura de diferencias se describen en
[Comparación mensual](documentacion/comparacion-mensual.md).
La presentación de importes emitidos y la navegación están en
[Recibos](documentacion/recibos.md).
Las reglas del resumen y la lectura de las barras se describen en
[Estadísticas y gráfico mensual](documentacion/estadisticas.md).

El HTTP de desarrollo se limita a direcciones locales. Android permite tráfico en
claro local solamente en la variante `debug`; la variante de publicación utiliza
HTTPS con certificados válidos. Para otra URL se compila con
`--dart-define=NIVORA_API_URL=https://TU_SERVIDOR`, reemplazando el marcador por el
servidor real. El modo release rechaza HTTP; todavía se requiere configurar el
servidor de publicación y la firma de distribución antes de publicar la aplicación.

## Alcance de esta entrega

Esta entrega conecta el acceso, el resumen, el historial, los costos, la proyección,
la comparación, las estadísticas y los recibos al backend existente. Las pantallas de administración y
notificaciones nativas se incorporarán en entregas siguientes. El ícono sigue siendo
el de Flutter; el nombre visible de la aplicación ahora es Nivora.

Las pruebas usan respuestas controladas, incluidas peticiones HTTP locales reales.
La comprobación contra MySQL, la empresa de agua y la fuente de identidad debe
realizarse con los servicios del entorno local. No se requiere modificar la base de
datos ni el backend para aplicar estos archivos.
