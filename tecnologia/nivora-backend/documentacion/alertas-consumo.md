# Alerta de consumo

Nivora compara los litros acumulados desde el inicio del mes actual hasta
la última lectura disponible con los litros de todo el mes anterior. Usa
los límites del calendario de `America/Lima` y exige cobertura completa
para ambos cálculos, según las reglas de
[consumo actual](consumo-actual.md) e [historial mensual](historial-mensual.md).

La alerta se genera cuando el acumulado actual es estrictamente mayor al
total anterior. Si los valores son iguales, se espera a que una nueva
lectura supere el umbral. Un mes anterior completo con cero litros también
sirve como referencia.

Después de una sincronización de lecturas completada y confirmada, Nivora
evalúa la regla para ese suministro. La base de datos conserva una alerta
por suministro y mes, con los litros de la primera detección, el corte de
medición y la fecha de registro. Los reintentos conservan esa detección.
Esta etapa registra las alertas en la base de datos.

## Comprobación local

Aplica `base-datos/14-alertas-consumo.sql` y
`base-datos/17-configuracion-notificaciones.sql` en el cliente MySQL. Compila
con `mvnw.cmd clean verify`. Desde el CMD del módulo, ejecuta lo siguiente
para evaluar las lecturas guardadas en MySQL para el suministro
`13100390`. Puedes elegir otro suministro registrado mediante
`--nivora.alertas-consumo.codigo-prueba`.

```cmd
powershell -NoProfile -Command "$clave = Read-Host 'Contrasena de nivora_app' -AsSecureString; $env:NIVORA_DB_CLAVE = [System.Net.NetworkCredential]::new('', $clave).Password; java -jar '.\target\nivora-backend-0.0.1-SNAPSHOT.jar' --spring.main.web-application-type=none --nivora.sincronizacion.habilitada=false --nivora.alertas-consumo.comprobar-al-iniciar=true"
```

| Resultado | Significado |
| --- | --- |
| `PAUSADA` | La evaluación de alertas está desactivada en el servidor o en la configuración administrativa. |
| `SIN_DATOS_COMPARABLES` | Alguno de los cálculos carece de cobertura completa. |
| `NO_SUPERA` | El acumulado actual es menor o igual al total anterior. |
| `GENERADA` | Se guardó la primera alerta para ese suministro y mes. |
| `YA_REGISTRADA` | El acumulado supera el umbral y la alerta del mes ya está guardada. |

La evaluación está habilitada por defecto. Puedes pausarla desde la
[configuración administrativa](configuracion-notificaciones.md).
La propiedad `nivora.alertas-consumo.habilitada=false` fuerza la pausa en
esa instancia del servidor.
