# Contratos externos comprobados

Revisión del 19 de noviembre de 2026. Cierra P06/EQ-07 para los proveedores
y escenarios definidos en el proyecto. La Empresa de Agua es el servicio
simulado de este repositorio. APIsPERU es el proveedor de identidad; no se
presenta como una conexión directa de Nivora con RENIEC.

## Matriz

Las seis primeras rutas son GET y relativas al origen configurado de la
Empresa de Agua. Los códigos de suministro se conservan como texto.

| Adaptador / ruta | Respuesta válida | Ausencia confirmada | Respuesta inválida o indisponibilidad | Evidencia automática |
|---|---|---|---|---|
| Suministro: `/api/v1/suministros/{codigo}` | 200 JSON, código solicitado y titular no vacío | 404 Problem JSON con `status=404`, `title=Suministro no encontrado` e `instance` igual a la ruta | 404 genérico, 3xx, 401, 429, 5xx, JSON inválido y timeout son fallo de integración | `ContratosEmpresaAguaTests`, `FuenteSuministrosHttpTests` |
| Inventario: `/api/v1/suministros/{codigo}/medidores` | Fuente, código, zona `America/Lima`, identificadores y fechas compatibles | Mismo contrato de 404; detiene la recuperación de ese suministro | No se acepta inventario incoherente, duplicado o de otro suministro | `ContratosEmpresaAguaTests`, `ContextoLecturasJsonTests` |
| Corte: `/api/v1/lecturas/corte` | Fuente, límite de identificador y confirmación temporal coherentes | No existe ausencia de suministro en esta ruta global; 404 es fallo | Corte inválido o inaccesible impide iniciar un contexto nuevo | `ContratosEmpresaAguaTests`, `RecuperacionConCorteTests` |
| Página: `/api/v1/suministros/{codigo}/lecturas` | Parámetros `despuesDe`, `limite` y `hastaId`; litros, tipo, lecturas, cursor y `hayMas` coherentes | 404 confirmado; una página vacía válida puede terminar el recorrido | Rechaza mezclas de suministros, unidades, tipos incompatibles, cursores inválidos y fechas inconsistentes | `ContratosEmpresaAguaTests`, `FuenteLecturasHttpTests`, casos de intervalo |
| Tarifas: `/api/v1/suministros/{codigo}/tarifas` | Fuente, suministro, PEN, zona, asignaciones y versiones/tramos sin contradicciones | 404 confirmado; `asignaciones=[]` es historial vacío válido | Error conserva el historial local; versiones contradictorias no sustituyen importes | `ContratosEmpresaAguaTests`, `TarifasIntegracionTests`, `SincronizacionTarifasTests` |
| Recibos: `/api/v1/suministros/{codigo}/recibos?limite=...` | PEN, suministro, orden mensual, fechas, versión e importes consistentes | 404 confirmado; lista vacía válida | Fallo de la fuente conserva recibos guardados; no transforma estimaciones en recibos | `ContratosEmpresaAguaTests`, `FuenteRecibosHttpTests`, `AccesoConsultaIntegradoTests` |
| Identidad: `GET https://dniruc.apisperu.com/api/v1/dni/{dni}` | 200 JSON, mismo DNI de ocho dígitos, nombres y apellidos; campos adicionales admitidos | 404 o `success=false`: resultado no concluyente; no crea sesión | 401/403: credencial rechazada; 429: límite; 5xx/timeout/desconexión: fuente no disponible; cuerpo incompatible: respuesta incompatible | `FuenteIdentidadApisPeruTests`, `AccesoConsultaIntegradoTests`, `TransporteHttpsTests` |
| Web Push: POST al endpoint HTTPS admitido de la suscripción | Cuerpo cifrado `aes128gcm`, VAPID ES256, audiencia del origen, TTL y Topic | 404/410: suscripción expirada | 408/429/5xx o fallo de transporte: reintento; otros rechazos no se tratan como entrega | `EmisorWebPushHttpTests`, `ColaWebPushTests`, `AccesoConsultaIntegradoTests` |

El cliente de agua limita conexión a 3 s y lectura a 5 s por defecto;
son configurables. Los límites de cuerpo son 64 KiB para suministro,
corte y recibos, 256 KiB para inventario/tarifas y 512 KiB para páginas.
Identidad limita conexión a 5 s, lectura a 10 s y cuerpo a 16 KiB.
Web Push limita conexión a 4 s y solicitud a 8 s.

Los lectores JSON de agua e identidad rechazan claves duplicadas y contenido
adicional después del documento. No se siguen redirecciones. Las pruebas
de agua ejecutan 78 casos: 6 válidos, 60 variantes de error, 6 ausencias
y 6 tiempos de espera. Las pruebas existentes completan las restricciones
de contenido de cada contrato y las lecturas de intervalo.

La prueba del emisor verifica el envío a través de su cliente controlado,
el cifrado y la firma VAPID; un receptor independiente descifra el
cuerpo según RFC 8291 y rechaza un secreto de autenticación distinto; sus resultados esperados se especifican de
forma independiente. Una aceptación del servicio Push no acredita que
un dispositivo haya mostrado la notificación. Esa parte corresponde a P08.

## Contraste con fuentes oficiales

- Empresa de Agua: controladores y pruebas de proveedor en
  `tecnologia/empresa-agua/src/test/java/pe/nivora/empresaagua/api/`.
  La regresión del proveedor comprueba sus rutas, formatos y errores.
- APIsPERU: [servicio DNI/RUC](https://www.apisperu.com/servicios/dniruc)
  y [OpenAPI publicado](https://dniruc.apisperu.com/doc/swagger), consultados
  el 19 de noviembre de 2026. Coinciden el origen, la ruta, los campos de
  identidad y la variante `success=false`.
- Web Push: [RFC 8030](https://www.rfc-editor.org/rfc/rfc8030.html),
  [cifrado RFC 8291](https://www.rfc-editor.org/rfc/rfc8291.html) y
  [VAPID RFC 8292](https://www.rfc-editor.org/rfc/rfc8292.html).

El OpenAPI de APIsPERU describe `token` en la consulta. El adaptador conserva
la cabecera `Authorization: Bearer`: el diagnóstico público con una credencial
ficticia y DNI `00000000` obtuvo `Token not found` sin credencial y
`Token inválido` tanto con Bearer como con el parámetro. Esto indica que
ambas formas llegan a la validación de credenciales del servicio observado;
no demuestra una consulta de identidad válida ni disponibilidad de una cuota.
El registro manual anterior del proyecto documenta el flujo completo en
Windows con sus fuentes configuradas en [verificación de acceso](verificacion-acceso.md).
No se incluyen tokens reales, respuestas personales ni el documento completo
del proveedor en las evidencias de esta revisión.

Para sustituir un proveedor real se contrasta su contrato y autenticación
con esta matriz y se ejecutan los casos de integración antes de utilizar sus
datos. Las diferencias se resuelven en su adaptador y composición.

## Repetición

Desde `tecnologia\nivora-backend`:

```bat
mvnw.cmd -Dtest=ContratosEmpresaAguaTests,FuenteIdentidadApisPeruTests,EmisorWebPushHttpTests test
```

`mvnw.cmd clean verify` ejecuta también la regresión de sesiones, persistencia,
reintentos, aislamiento y continuidad de consultas.
