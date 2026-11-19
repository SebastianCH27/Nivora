# Incorporación y actualización automática de tarifas

Corrección posterior a `457b96c`, 19 de noviembre de 2026, America/Lima.

El trabajador de tarifas descubre los suministros registrados en Nivora,
incorpora su primer historial y vuelve a consultarlo según su calendario.
Un alta HTTP de consulta guarda el suministro; el siguiente ciclo elegible
lo descubre sin ejecutar el comprobador manual. La primera consulta de costos
puede responder `SIN_TARIFA_COMPATIBLE` mientras la incorporación está pendiente.

`SincronizarTarifas` utiliza el importador existente. La programación se guarda
en MySQL, con reserva temporal, último intento, último éxito, resultado,
fallos consecutivos y próxima fecha. Reiniciar Nivora conserva ese calendario.
La llamada externa ocurre fuera de la transacción que reserva el trabajo.
Una reserva vencida se puede recuperar; su propietario anterior no confirma
el calendario de una reserva nueva.

## Incorporación y continuidad

| Resultado | Comportamiento |
|---|---|
| Historial válido | Conserva versiones y guarda la revisión correspondiente; reinicia los fallos y programa el refresco. |
| Historial idéntico | Conserva el número de revisión y actualiza su fecha de consulta. |
| Nueva versión o asignación válida | Crea una revisión y conserva las anteriores. |
| Caída o respuesta incompatible | `ERROR_FUENTE`; conserva el último historial aceptado y programa reintento. |
| Suministro ausente confirmado por el contrato | `SUMINISTRO_NO_ENCONTRADO`; conserva los datos y reintenta. |
| Cambio de condiciones de una versión ya guardada | `CONFLICTO`; revierte la incorporación y reintenta sin reemplazar esa versión. |
| Fallo de almacenamiento | `ERROR_ALMACENAMIENTO`; conserva los historiales aceptados y reintenta cuando la programación está disponible. |

Una lista de asignaciones vacía confirmada con HTTP 200 sigue siendo una
revisión válida sin asignaciones, según el contrato existente. No equivale
a una caída ni a la ausencia del suministro.

Los costos, proyecciones y comparaciones siguen usando el historial local.
Una consulta del panel no dispara una llamada al proveedor. Las tarifas
administrativas conservan la prioridad y las reglas de vigencia existentes;
el refresco actualiza únicamente el historial importado de `empresa-agua`.

El error de un suministro no impide intentar los siguientes dentro del
límite del ciclo. Si falla la programación misma, el siguiente ciclo vuelve
a intentar su acceso y puede recuperar las reservas vencidas.

## Configuración

Propiedades bajo `nivora.tarifas.sincronizacion`:

| Propiedad | Valor inicial |
|---|---:|
| `habilitada` | `true` |
| `espera-inicial-ms` | 5000 |
| `intervalo-ms` | 10000 |
| `maximo-intentos-por-ciclo` | 2 |
| `reserva-segundos` | 60 |
| `intervalo-completo-segundos` | 300 |
| `reintento-inicial-segundos` | 30 |
| `reintento-maximo-segundos` | 1800 |

El planificador espera diez segundos después de terminar cada ciclo.
Un suministro actualizado vuelve a ser elegible al menos 300 segundos
después del éxito. La cola, el tiempo de red y las demás tareas también
influyen; estos valores no garantizan refrescar todos los suministros cada
cinco minutos. Los reintentos crecen 30, 60, 120… hasta 1800 segundos.

La política admite de 1 a 1000 intentos por ciclo y duraciones enteras de
1 a 86400 segundos. El reintento inicial no puede superar su máximo.
La reserva debe dimensionarse para la red y el trabajo previstos.

La tarea está habilitada en el arranque web habitual. El perfil `test`
la desactiva, salvo la prueba específica que verifica el planificador real.
El modo `--spring.main.web-application-type=none` no activa el planificador.
El comprobador manual sigue disponible para operaciones explícitas.

## Aplicar la corrección en Windows

1. Detener el JAR de Nivora con Ctrl+C y copiar los archivos de la entrega
   sobre la carpeta del repositorio, conservando la estructura `tecnologia`.
2. Con MySQL encendido, abrir su cliente con una cuenta administradora:

```bat
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p
```

3. En `mysql>`, después de las migraciones existentes hasta la 21, ejecutar:

```sql
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/nivora-backend/base-datos/22-sincronizacion-tarifas.sql;
SHOW TABLES FROM nivora LIKE 'programacion_sincronizacion_tarifas';
EXIT;
```

La migración agrega una tabla. Repetirla conserva historiales, programaciones
y reservas. El usuario de aplicación utiliza los permisos DML ya asignados
a `nivora.*`; no se cambian sus privilegios ni se crean tablas al arrancar.

4. Desde CMD, reconstruir:

```bat
cd /d C:\Users\HP\Desktop\Nivora\tecnologia\nivora-backend
mvnw.cmd clean verify
```

5. Iniciar el nuevo JAR con las mismas variables de conexión e identidad
   usadas anteriormente y mantener Empresa de Agua en 8081. Si las variables
   siguen definidas en ese CMD:

```bat
java -jar target\nivora-backend-0.0.1-SNAPSHOT.jar
```

Un nuevo CMD necesita volver a recibir sus variables. Los pasos completos
de inicio están en [sesiones de consulta](sesiones-consulta.md#probar-desde-windows).

## Comprobar el resultado

Después de un inicio de sesión exitoso, consultar en MySQL:

```sql
SELECT codigo_suministro, ultimo_resultado, fallos_consecutivos,
       ultimo_intento_utc, ultimo_exito_utc, proximo_intento_utc
FROM nivora.programacion_sincronizacion_tarifas
ORDER BY codigo_suministro;

SELECT codigo_suministro, revision, consultado_en_utc
FROM nivora.tarifas_historiales
WHERE fuente = 'empresa-agua'
ORDER BY codigo_suministro, revision;
```

Las fechas anteriores son UTC. Con la fuente disponible, el resultado debe
llegar a `ACTUALIZADO` y debe existir una revisión importada. El log informa
`Sincronizacion de tarifas: suministro ...; revision ...`.
Los estados de programación de tarifas se inspeccionan en esta tabla y en
el log; esta corrección no añade rutas de supervisión ni reintento HTTP.

El importe final disponible depende además de las lecturas y de los conceptos
informados por la tarifa. La tarifa de demostración de Empresa de Agua omite
alcantarillado e IGV: puede producir `CONCEPTOS_INCOMPLETOS` aunque la importación
automática haya terminado correctamente. No se sustituyen esos conceptos por cero.

## Validación y alcance

La regresión completa tiene 656 pruebas en 37 clases, cero fallos, errores
u omitidas y JAR empaquetado. Son 24 casos nuevos: 17 de programación,
reservas, reintentos e integridad; uno con el planificador real después de
un alta HTTP; y seis HTTP de costos, fallos, recuperación y prioridad administrativa.
La [evidencia](evidencias/sincronizacion-tarifas-2026-11-19.json) conserva los resultados.

Con MySQL 8.0.46 aislado pasaron 130 casos: los 128 de la suite HTTP
integrada completa, uno con el planificador real y uno de repetición de la
migración con conservación del historial y concurrencia entre dos instancias. La copia de validación cambia
solo las conexiones y nombres de bases desechables de las pruebas.

El preparador de carga incluye la migración 22. Su escenario histórico usa
tarifas precargadas y una fuente ficticia sin ruta tarifaria: ese ejecutor
desactiva explícitamente este trabajador y lo registra en `entorno.json`.
La carga histórica de 5000 usuarios conserva su alcance; no certifica el
rendimiento de la nueva sincronización. La política habitual permanece habilitada.

La comprobación breve del ejecutor de carga terminó con 20 usuarios durante
45 segundos de medición, sin fallos del generador y con umbrales y continuidad
aprobados. Sirve para verificar la preparación de la nueva tabla y el
arranque del escenario existente; no mide el trabajador tarifario, que ese
escenario desactiva explícitamente.
