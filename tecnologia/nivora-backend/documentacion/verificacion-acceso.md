# Verificación del bloque de acceso

La parte 6 comprueba la integración entre las rutas de acceso, los
adaptadores HTTP y el almacenamiento de sesiones. Usa Nivora en un puerto
local, H2 y respuestas ficticias de la Empresa de Agua y de APIsPERU. El
adaptador de identidad envía una credencial ficticia al servidor de pruebas.

| Caso | Comprobación |
|---|---|
| Suministro y DNI válidos | Nivora consulta ambas rutas HTTP, conserva los ceros iniciales y registra el acceso. El token de APIsPERU viaja en la cabecera Bearer. |
| Suministro ausente confirmado | HTTP 401. La validación se detiene antes de consultar la identidad. |
| Ruta de suministro incompatible | HTTP 503. El fallo conserva su significado de integración. |
| Credencial, cuota o servicio de identidad indisponible | HTTP 503. La respuesta protege el DNI y el token del proveedor. |
| HTTP 200 de identidad sin un resultado confirmado | HTTP 503 y cero sesiones creadas. |
| Proveedores indisponibles durante un nuevo acceso | HTTP 503. Una sesión existente conserva su vigencia. |
| Permisos por tipo de sesión | Consulta y administración acceden a sus rutas autorizadas. El uso de un token en rutas del otro tipo recibe HTTP 401. |
| Cierre de sesión de consulta | HTTP 204. Un intento posterior con ese token recibe HTTP 401. |

Las pruebas de las partes anteriores cubren formato de entrada, vencimiento,
límite de intentos, operaciones concurrentes y reversión de la escritura
ante un fallo de almacenamiento. La comprobación de la parte 5 con MySQL
8.0.46 incluyó la conservación de una sesión después de reiniciar.

Desde CMD, en `tecnologia\nivora-backend`, ejecuta toda la suite.

```bat
mvnw.cmd clean verify
```

Para ejecutar el conjunto de integración de forma aislada, usa este comando.

```bat
mvnw.cmd -Dtest=AccesoConsultaIntegradoTests test
```

La prueba en Windows con la Empresa de Agua, MySQL y APIsPERU confirmó la
secuencia **201 → 200 → 204 → 401**. Se puede repetir con los pasos de
[Sesiones de consulta](sesiones-consulta.md). Las operaciones con una cuenta
administrativa se describen en [Sesiones administrativas](sesiones-administrativas.md).

Para el escenario EQ-06 quedan pendientes la prueba de un despliegue con
HTTPS y la verificación de las futuras rutas de consulta de registros de acceso.
