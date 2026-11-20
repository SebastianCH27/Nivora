# Historial mensual en Flutter

Base de la entrega: `main`, commit `b72a65c37cf34b73a3c810d6b638f6a382007642`.
El backend conserva su contrato. No se agregan dependencias ni migraciones.

## Uso

Después de ingresar con suministro y DNI, abre **Historial mensual**.
El historial se consulta por primera vez al abrir la pestaña. El consumo actual
permanece disponible al regresar a **Consumo actual**.

- El selector permite consultar 3, 6 o 12 meses terminados, del más reciente
  al más antiguo. El mes actual se consulta en la otra pestaña.
- **Meses anteriores** retrocede una ventana del tamaño seleccionado.
- **Más recientes** avanza una ventana. Al alcanzar la más reciente, el backend
  vuelve a elegir el último mes terminado en Lima.
- **Últimos meses** regresa directamente al rango reciente.
- **Actualizar historial** vuelve a consultar el rango seleccionado.
- Pulsa un mes para ver la explicación de cobertura y, cuando exista,
  el tramo medido con fechas en hora de Lima.

## Valores y estados

| Estado del backend | Presentación |
|---|---|
| `COMPLETO` | Mes completo, m³ y litros registrados. Un cero medido se muestra como cero. |
| `PARCIAL` | Consumo parcial, valor conocido y aviso de que falta parte del mes. |
| `SIN_DATOS` | Sin datos suficientes y sin valor disponible. No equivale a cero. |
| `INCOMPATIBLE` | Lecturas en revisión y sin valor disponible. |

Los m³ y litros se mantienen como cadenas decimales. La pantalla no suma valores
parciales ni calcula totales, costos o promedios. Se valida que las unidades
coincidan sin convertirlas a `double`, que los meses sean consecutivos y que
correspondan al suministro, rango y límites mensuales de Lima esperados.

## Conexión y sesión

Se usa `GET /api/v1/acceso/consulta/historial` con `Authorization: Bearer`.
La consulta incluye `meses`; `hasta` solo se indica para una ventana antigua.
La selección inicial se basa en el reloj del backend, no en el del teléfono.

Un fallo de red, HTTP o formato muestra un mensaje y permite reintentar.
Si falla una actualización del mismo rango, los datos previos se conservan
identificados como anteriores. Al cambiar de filtro se retiran esos datos,
incluso si la nueva consulta falla. Un HTTP 401, el vencimiento o el cierre
eliminan la sesión, el consumo y el historial. Una respuesta pendiente de una
sesión anterior se descarta, aunque ya se haya iniciado otra sesión.

## Prueba en la PC y en el teléfono

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

Las pruebas cubren peticiones HTTP locales con Bearer y parámetros, formato y
orden de los meses, decimales, límites de Lima, errores, consultas pendientes,
limpieza al salir y navegación de la interfaz en anchos de 320 y 1200 píxeles.
Usan respuestas controladas. No sustituyen la comprobación con los servicios
reales de la PC.

Para Windows, con los servicios habituales disponibles:

```cmd
flutter run -d windows
```

Para Android, sigue los pasos de compilación, conexión ADB y puente al puerto
8082 del [README](../README.md#probar-en-el-samsung-galaxy-s22).

Comprueba estos casos en el suministro que vienes utilizando:

1. El acceso y el consumo actual siguen funcionando.
2. Historial muestra los últimos 12 meses terminados. En noviembre de 2026,
   el primero debe ser octubre de 2026 y el último noviembre de 2025.
3. Cambia a 3 y a 6 meses. Recorre meses anteriores y regresa a los últimos.
4. Abre un mes y revisa sus unidades y cobertura. Puede haber meses sin datos;
   el resultado depende de las lecturas que tenga guardadas el backend.
5. Para contrastar valores, consulta el mismo rango del endpoint del backend
   siguiendo su documentación `documentacion/historial-mensual.md`. No hace
   falta modificar MySQL ni inventar datos para llenar la pantalla.
6. Si detienes el backend después de consultar, **Actualizar historial** debe
   mostrar el fallo y conservar el rango anterior con su aviso. Al restablecer
   el servicio, **Reintentar historial** debe recuperar la consulta.
7. Cierra sesión desde el historial: debe volver el formulario sin los datos
   del suministro. Vuelve a ingresar y verifica también el consumo actual.

El commit y el push se realizan manualmente después de estas comprobaciones.
