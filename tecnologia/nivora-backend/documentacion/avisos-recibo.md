# Aviso de recibo disponible

Nivora registra un aviso por cada recibo guardado cuya fecha de disponibilidad
sea igual o anterior a la fecha de hoy en `America/Lima`. La tarea revisa hasta
100 recibos pendientes por ciclo y se ejecuta cada minuto en el backend web.
Lee la base local, por lo que también procesa los recibos guardados antes de
aplicar la migración, incluso si su fecha límite ya pasó.

Cada aviso referencia el recibo por suministro y periodo. La fecha límite de
pago se consulta en el recibo emitido. Las ejecuciones repetidas o concurrentes
conservan el primer aviso y su fecha de registro. Esta etapa guarda el aviso
para su consulta y envío en las etapas siguientes.

## Comprobación local

Después de aplicar `base-datos/15-avisos-recibo.sql` y compilar, ejecuta este
comando desde el CMD de `nivora-backend` para revisar los recibos guardados.

```cmd
powershell -NoProfile -Command "$clave = Read-Host 'Contrasena de nivora_app' -AsSecureString; $env:NIVORA_DB_CLAVE = [System.Net.NetworkCredential]::new('', $clave).Password; java -jar '.\target\nivora-backend-0.0.1-SNAPSHOT.jar' --spring.main.web-application-type=none --nivora.sincronizacion.habilitada=false --nivora.avisos-recibo.comprobar-al-iniciar=true"
```

El log `Avisos de recibo: nuevos N.` informa cuántos avisos se guardaron en el
ciclo. Si todos los recibos disponibles ya tienen un aviso, el resultado es
`nuevos 0`. Para comprobar el periodo, las fechas y el registro de un
suministro, ejecuta en `mysql>` lo siguiente.

```sql
SELECT a.periodo, r.fecha_disponibilidad, r.fecha_vencimiento, a.registrado_en_utc
FROM nivora.avisos_recibo a
JOIN nivora.recibos r
  ON r.codigo_suministro = a.codigo_suministro AND r.periodo = a.periodo
WHERE a.codigo_suministro = '13100390'
ORDER BY a.periodo DESC;
```

La tarea automática está habilitada por defecto. Se puede pausar con
`nivora.avisos-recibo.habilitados=false`.
