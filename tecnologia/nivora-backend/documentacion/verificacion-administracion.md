# Verificación integrada del bloque 8 de administración

El caso `flujoAdministrativoRecuperaLecturasYRecibosConCalendarioPublicado`
usa la API REST de Nivora, sus sesiones, los repositorios JDBC y los
procesos de sincronización. Una Empresa de Agua ficticia permite simular
la caída y la recuperación. Un reloj de prueba permite adelantar los
turnos sin esperar los intervalos reales.

## Recorrido verificado

| Paso | Resultado comprobado | Requisito |
| --- | --- | --- |
| Publicar el día 5 de inicio desde diciembre de 2026. | La regla guarda revisión 1, autor y vigencia. El 2 de diciembre, el periodo de análisis activo sigue siendo noviembre en Lima. | RF32 |
| Consultar el suministro `000101` y su registro de acceso. | Se conservan los ceros iniciales y se devuelve el DNI como `******01`. | RF31 |
| Procesar la primera página de lecturas y provocar el fallo de la siguiente y del proveedor de recibos. | Se conserva el cursor `1`, la lectura aceptada y el recibo importado previamente. La supervisión informa dos incidencias abiertas. | RF35, RF36 |
| Consultar el recibo con la fuente caída. | El recibo conserva S/ 13,46 y su fin de periodo facturado el 1 de diciembre. El periodo de análisis termina el día 5 y la API marca que los rangos difieren. | RF32, RF35 |
| Solicitar ambos reintentos. | El token de consulta recibe HTTP 401. El administrador recibe HTTP 202 mientras los turnos originales siguen en el futuro. | RF30, RF37 |
| Restablecer la fuente y ejecutar los turnos adelantados. | Las páginas se solicitaron con cursores `0, 1, 1`. La recuperación añade la segunda lectura, conserva un solo recibo y cierra ambas incidencias. | RF36, RF37 |
| Cerrar la sesión administrativa. | El token cerrado recibe HTTP 401 al consultar accesos y al pedir un reintento. La sesión de consulta conserva acceso al consumo calculado de 400 L. | RF30 |

Los casos complementarios cubren la caducidad y los permisos, los registros
paginados, el conflicto de revisiones, los límites de meses cortos y un
reintento impedido por una reserva vigente o un recorrido bloqueado. La
[verificación de tarifas](verificacion-tarifas.md) documenta las vigencias
administrativas de RF33. La
[configuración de notificaciones](configuracion-notificaciones.md) documenta
las operaciones autorizadas para RF34.

## Repetir en Windows

Desde CMD, en `tecnologia\nivora-backend`, ejecuta la suite completa.

```bat
mvnw.cmd clean verify
```

Para ejecutar solo el recorrido de esta parte, usa esta línea.

```bat
mvnw.cmd -Dtest=AccesoConsultaIntegradoTests#flujoAdministrativoRecuperaLecturasYRecibosConCalendarioPublicado test
```

El recorrido usa una base de pruebas H2 y fuentes HTTP locales controladas.
También se ejecutó el mismo caso con una base aislada de MySQL 8.0.46.
