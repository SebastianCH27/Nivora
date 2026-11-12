# Configuración de notificaciones

Aplica `base-datos/17-configuracion-notificaciones.sql` en MySQL con las
tablas de suministros y administradores ya creadas. El script es repetible
y conserva las configuraciones guardadas.

## Preferencia por dispositivo

La aplicación cliente conserva un UUID por instalación y lo envía en la ruta
`/api/v1/acceso/consulta/notificaciones/dispositivos/{id}`. Usa su forma
canónica en minúsculas. Nivora obtiene el suministro de la sesión de consulta.
Si el mismo dispositivo se usa con dos suministros, cada uno tiene su propia
preferencia.

| Método | Resultado |
|---|---|
| `GET` | HTTP 200 con la preferencia del dispositivo para el suministro de la sesión. HTTP 404 antes de registrarla. |
| `PUT` | HTTP 200. Crea o actualiza la preferencia. Un reintento idéntico conserva `actualizadaEn`. |

El cliente informa el permiso concedido por el dispositivo. El campo
`permiso` admite `CONCEDIDO`, `DENEGADO` y `PENDIENTE`. Solo se puede
guardar `habilitadas: true` con permiso `CONCEDIDO`. Si el permiso
cambia a `DENEGADO` o `PENDIENTE`, el cliente guarda `habilitadas: false`.

```json
{"permiso":"CONCEDIDO","habilitadas":true}
```

La respuesta incluye `idDispositivo`, `permiso`, `habilitadas` y
`actualizadaEn` en UTC. El envío al dispositivo usará estas preferencias.

## Control administrativo de alertas de consumo

`GET /api/v1/administracion/notificaciones/alertas-consumo` consulta el
estado y `PUT` sobre la misma ruta lo cambia. Ambas operaciones requieren
`GESTIONAR_ALERTAS`, que las cuentas administrativas activas reciben
cuando completan el cambio de clave.

```json
{"habilitada":false}
```

La respuesta HTTP 200 incluye `habilitada` para la configuración
administrativa, `efectiva` para el estado que usa esta instancia y
`actualizadaPor` y `actualizadaEn` para el último cambio. Por defecto la
configuración administrativa está habilitada, con actor y fecha nulos.
Si `nivora.alertas-consumo.habilitada` es `false`, esa instancia mantiene
`efectiva: false`.

El evaluador consulta el estado al empezar cada evaluación. El cambio
se conserva entre reinicios. Durante la pausa se siguen sincronizando las
lecturas, se conservan las alertas previas y siguen generándose los avisos
de recibo. Al reanudar se aplica la regla existente de comparación con
el total del mes anterior.

## Comprobación

Arranca el JAR compilado y usa `Authorization: Bearer ...` con el token
de la sesión correspondiente. Consulta una preferencia nueva, actívala
con permiso concedido, vuelve a consultarla y desactívala. Para verificar
la pausa, consulta el control administrativo, guarda `habilitada: false`
y verifica `efectiva: false`. Restaura el estado que encontraste al
empezar la prueba.

La API devuelve HTTP 400 para valores inválidos, HTTP 401 cuando falta la
sesión correspondiente, HTTP 403 si falta el permiso administrativo y
HTTP 503 si falla el acceso a la configuración. Las respuestas incluyen
`Cache-Control: no-store`.
