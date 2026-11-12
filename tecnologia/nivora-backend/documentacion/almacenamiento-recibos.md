# Almacenamiento de recibos emitidos

El importador consulta Empresa de Agua y guarda sus recibos en MySQL
para un suministro previamente registrado en Nivora. Conserva el importe
definitivo en PEN, consumo, cargos, fechas de disponibilidad y vencimiento,
número de recibo y referencia tarifaria entregados por la fuente.
`recibido_en_utc` indica la primera recepción en Nivora y se guarda con
precisión de microsegundos. El periodo se representa con el primer día
del mes en el calendario de Lima.

| Resultado de la importación | Comportamiento |
|---|---|
| Recibo de un periodo nuevo | Se guarda en el suministro consultado. |
| Reenvío idéntico | Cuenta como repetido y conserva su fecha de recepción. |
| Datos distintos para un periodo guardado o un número de recibo ya usado | La transacción revierte el lote y conserva los recibos anteriores. |
| Lista vacía o ventana más corta que el historial local | Se conservan los recibos que ya estaban guardados. |
| Suministro ausente en Empresa de Agua o fallo de la fuente | Se conservan los recibos que ya estaban guardados. |

La clave primaria reúne el código del suministro y el periodo. El número
del recibo también es único, como en Empresa de Agua. Las importaciones
del mismo suministro se serializan en MySQL, incluso si llegan desde
instancias distintas.

## Preparar MySQL

En el cliente MySQL, con una cuenta autorizada para crear tablas, ejecuta
el script después de haber creado `suministros`.

```sql
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/nivora-backend/base-datos/12-recibos.sql;
SHOW TABLES FROM nivora LIKE 'recibos';
```

## Comprobar en Windows

Desde el CMD de `tecnologia\nivora-backend`, compila con
`mvnw.cmd clean verify`. Con Empresa de Agua activa en 8081, ejecuta
el comando de importación. Pide la contraseña de `nivora_app`,
consulta hasta 12 recibos disponibles y termina.

```bat
powershell -NoProfile -Command "$clave = Read-Host 'Contrasena de nivora_app' -AsSecureString; $env:NIVORA_DB_CLAVE = [System.Net.NetworkCredential]::new('', $clave).Password; java -jar '.\target\nivora-backend-0.0.1-SNAPSHOT.jar' --spring.main.web-application-type=none --nivora.sincronizacion.habilitada=false --nivora.recibos.almacenamiento.comprobar-al-iniciar=true"
```

El suministro predeterminado es `13100390`. Si el conjunto de recibos
emitidos es el mismo, al repetir el comando deben aparecer cero nuevos
y los recibos que ya se guardaron como repetidos.

Para revisar los valores en MySQL, ejecuta esta consulta en el cliente.

```sql
SELECT codigo_suministro, periodo, numero_recibo, importe_total, fecha_vencimiento, recibido_en_utc
FROM nivora.recibos WHERE codigo_suministro = '13100390' ORDER BY periodo DESC;
```
