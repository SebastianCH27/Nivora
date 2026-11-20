# Nivora: aplicación para Android y Windows

Primera entrega funcional del frontend: acceso mediante código de suministro y DNI,
consulta del consumo actual, actualización manual y cierre de sesión. Usa el contrato
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

6. Abre el APK que acabas de compilar, sin repetir Gradle:

```cmd
flutter run -d 192.168.18.41:44219 --use-application-binary="build\app\outputs\flutter-apk\app-debug.apk"
```

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
- `DELETE /api/v1/acceso/consulta/sesion`: revoca la sesión al salir.
- El token se mantiene únicamente en memoria. El formulario con el DNI se destruye
  al ingresar. El cierre de la aplicación no conserva el acceso.
- Al vencer la sesión o recibir 401 en una consulta, se borran los datos y vuelve
  el formulario. Se comprueba el vencimiento también al regresar a la aplicación.
- 400, 401, 429, 503, desconexiones, respuestas inválidas y espera agotada tienen
  mensajes visibles. `Retry-After` impide repetir el acceso antes de tiempo.
- Si falla una actualización, se identifica el resultado anterior como tal.
- Si falla el cierre remoto, se elimina la sesión local y se informa que no se
  pudo confirmar la revocación; el servidor conserva su vencimiento habitual.
- Los valores de consumo se conservan como cadenas decimales exactas. `SIN_DATOS`
  e `INCOMPATIBLE` no se representan como cero. Se indica la cobertura parcial,
  el tramo medido y la última lectura; las fechas se muestran en hora de Lima.
- Las peticiones tienen un límite total de 20 segundos y no siguen redirecciones.

El HTTP de desarrollo se limita a direcciones locales. Android permite tráfico en
claro local solamente en la variante `debug`; la variante de publicación utiliza
HTTPS con certificados válidos. Para otra URL se compila con
`--dart-define=NIVORA_API_URL=https://TU_SERVIDOR`, reemplazando el marcador por el
servidor real. El modo release rechaza HTTP; todavía se requiere configurar el
servidor de publicación y la firma de distribución antes de publicar la aplicación.

## Alcance de esta entrega

Esta entrega conecta el acceso y el resumen de consumo al backend existente. Las
pantallas de historial, costos, proyección, comparación, recibos, administración y
notificaciones nativas se incorporarán en entregas siguientes. El ícono sigue siendo
el de Flutter; el nombre visible de la aplicación ahora es Nivora.

Las pruebas usan respuestas controladas, incluidas peticiones HTTP locales reales.
La comprobación contra MySQL, la empresa de agua y la fuente de identidad debe
realizarse con los servicios del entorno local. No se requiere modificar la base de
datos ni el backend para aplicar estos archivos.
