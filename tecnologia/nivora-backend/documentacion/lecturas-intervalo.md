# Lecturas de intervalo

Ampliación de RF10 y cierre funcional de P01 después de `cc65d63`,
19 de noviembre de 2026, America/Lima.

Nivora incorpora dos tipos de medición en litros enteros. Una lectura
`ACUMULADA` expresa el contador total del medidor: el cálculo resta sus
extremos compatibles. Una lectura `INTERVALO` expresa directamente el
consumo del tramo `[inicioIntervalo, fechaMedicion)`: el cálculo suma los
intervalos completos contenidos y comprueba su cobertura.

## Contrato de la fuente

Se amplía la respuesta de `GET /api/v1/suministros/{codigo}/lecturas` que
consume el adaptador de Nivora. La página identifica un único tipo y unidad.
Las reglas de cursor, corte y paginación se conservan. Ejemplo ficticio:

```json
{
  "fuente": "empresa-agua",
  "codigoSuministro": "000101",
  "tipoMedicion": "INTERVALO",
  "unidad": "L",
  "lecturas": [
    {
      "idLectura": "1",
      "idMedidor": "1",
      "inicioIntervalo": "2026-11-01T05:00:00Z",
      "fechaMedicion": "2026-11-06T05:00:00Z",
      "valorLitros": 1000
    },
    {
      "idLectura": "2",
      "idMedidor": "1",
      "inicioIntervalo": "2026-11-06T05:00:00Z",
      "fechaMedicion": "2026-11-11T05:00:00Z",
      "valorLitros": 1200
    }
  ],
  "siguienteDespuesDe": "2",
  "hayMas": false
}
```

El suministro procede de la solicitud y los identificadores mantienen
su formato textual canónico. Ambos instantes usan UTC con sufijo `Z`.
El inicio debe ser anterior al fin y el consumo puede ser cero. El adaptador
rechaza una página con inicio ausente, duración inválida, unidad no admitida,
litros fraccionarios o tipo contradictorio. No avanza el cursor de una
respuesta incompatible.

La política de incorporación comprueba rango de fechas y precisión de
microsegundos, recepción, corte e inventario. La fecha de instalación y
retiro cubre todo el tramo, considerando que su fin es exclusivo y las
fechas del inventario son civiles de Lima.

La implementación habitual de Empresa de Agua sigue proporcionando
acumuladas. La ampliación del consumidor se demuestra con fuentes HTTP
controladas que entregan intervalos; este paquete no cambia el generador
ni las tablas del módulo Empresa de Agua.

## Persistencia e integridad

La tabla `lecturas` conserva su clave por fuente e identificador y añade
`inicio_intervalo_utc`, nullable para acumuladas. La restricción exige
inicio nulo para `ACUMULADA`, e inicio presente y anterior al fin para
`INTERVALO`. Se mantiene la identidad de una medición por suministro,
medidor y fecha final.

La reserva transaccional existente por fuente serializa las incorporaciones.
Un intervalo idéntico se cuenta como repetido y conserva la primera recepción.
Un identificador con otros datos no reemplaza el aceptado. Otro ID para la
misma medición y los intervalos superpuestos generan incidencias. Dos
intervalos que se tocan en un extremo son contiguos y se admiten.

La comprobación de superposición se aplica entre intervalos de la misma
fuente y suministro, incluso si declaran medidores diferentes. Un lote no
se confirma parcialmente ante un fallo de almacenamiento de sus incidencias.
Los contadores acumulados se comparan solo con otros acumulados. La huella
de las incidencias acumuladas conserva su composición anterior.

## Cálculos y cobertura

| Caso | Respuesta |
|---|---|
| Intervalos contiguos desde el inicio hasta el corte | `COMPLETO`; suma exacta de sus litros. Un único intervalo puede cubrir el tramo. |
| Falta un extremo | `PARCIAL`, con `FALTA_LECTURA_INICIAL` o `FALTA_LECTURA_FINAL`; conserva el consumo medido. |
| Existe un hueco entre intervalos | `PARCIAL`, `HUECOS_ENTRE_INTERVALOS`; conserva el subtotal conocido. |
| Un intervalo cruza un límite del periodo | No se reparte ni se prorratea. Se informa `INTERVALO_CRUZA_LIMITE`; si hay otros intervalos completos, su suma es parcial; si no los hay, el consumo es null. |
| Hay tipos mezclados dentro del periodo | `INCOMPATIBLE`, `TIPOS_DE_MEDICION_MEZCLADOS`, sin consumo. |
| Hay superposición o cambio de medidor en el cálculo | `INCOMPATIBLE`, con el motivo correspondiente. |
| La suma supera el rango de litros admitido | `INCOMPATIBLE`, `SUMA_FUERA_DE_RANGO`, sin desbordar a un valor negativo. |

El historial utiliza los cortes del calendario mensual configurado.
Un intervalo terminado exactamente en el inicio del mes siguiente pertenece
al periodo anterior. Su fecha sigue disponible como última medición aunque
el nuevo mes aún no tenga datos. Para detectar cruces históricos se consultan
los intervalos ya conocidos a la fecha de consulta, sin usar mediciones futuras.

Los costos utilizan el volumen disponible y conservan su estado de cobertura.
Una cobertura parcial no habilita la proyección ni la alerta de comparación.
La proyección conserva el mínimo de un día y calcula el ritmo únicamente
cuando el tramo observado es completo desde el inicio del periodo.

Consumo, historial, estadísticas, costos y proyección publican ahora
`tipoMedicion`: `ACUMULADA`, `INTERVALO` o null cuando no se identifica un
tipo único o no hay lecturas. Los campos de volumen siguen siendo cadenas
decimales. Las referencias inicial/final corresponden a los intervalos
incluidos; cuando solo hay uno, ambos identificadores coinciden.
El [contrato OpenAPI](openapi.json) incluye el campo y los nuevos motivos.

## Casos integrados y resultados esperados

La fuente HTTP ficticia proporciona 1000 L del 1 al 6 de noviembre y 1200 L
del 6 al 11. Octubre tiene un intervalo completo de 2000 L. Con una tarifa
ficticia de S/ 2,50 por m³, cargo fijo S/ 5,00 e IGV definido en cero:

| Resultado | Esperado |
|---|---:|
| Consumo actual hasta el 11 de noviembre | 2200 L |
| Historial de octubre | 2000 L, completo |
| Diferencia de consumo | +200 L |
| Costo actual estimado | S/ 10,50 |
| Costo estimado de octubre | S/ 10,00 |
| Proyección de noviembre, con 10 días medidos | 6600 L; S/ 21,50 |

Repetir la importación no duplica las tres lecturas. Con la fuente caída,
las consultas de la sesión siguen respondiendo con los datos locales.
La evaluación genera una sola alerta. Otros casos cubren huecos, mezcla,
cruce de mes, último dato en el límite y calendario configurado para el día 5.

## Validación realizada

- `clean verify`: 602 pruebas en 34 clases, cero fallos, errores u omitidas;
  `BUILD SUCCESS` y JAR empaquetado. Se agregaron 40 casos a la base de 562.
- MySQL 8.0.46 aislado: seis casos HTTP de intervalos y una comprobación
  auxiliar de migración, restricciones y concurrencia, todos aprobados.
  La migración parte de la definición anterior y conserva los registros;
  aplicarla otra vez mantiene los datos y el nuevo esquema.
- OpenAPI: 40 operaciones de negocio y una de salud, 101 esquemas,
  62 registros y 15 enums Java enlazados. Los tres controles de documentación
  forman parte de las 602 pruebas.

Las clases nuevas son `ConsumoIntervalosTests` y `LecturasIntervaloJdbcTests`.
Los recorridos HTTP están en los métodos `intervalos*` de
`AccesoConsultaIntegradoTests`. Se amplían `FuenteLecturasHttpTests` y
`RecuperacionConCorteTests` para incluir respuestas de intervalo, paginación,
fallo de página y reanudación del mismo corte. La comprobación auxiliar de
migración se ejecutó únicamente en la copia de validación con MySQL.

Los resultados, métodos y huellas están en
[la evidencia de intervalos](evidencias/lecturas-intervalo-2026-11-19.json).
La evidencia previa de 5000 usuarios conserva su JAR de `2d43391`; esta
ampliación requiere su propia evaluación de rendimiento para extrapolar
esa capacidad a la nueva versión. La ampliación posterior de
[comparación de costos](comparacion-costos.md) completa RF20/P02.

## Aplicar en Windows

1. Copiar los archivos del paquete sobre el repositorio.
2. Detener el JAR de Nivora con Ctrl+C antes de reconstruir o migrar.
3. Desde CMD en el backend, ejecutar las pruebas y generar el JAR:

```bat
cd /d C:\Users\HP\Desktop\Nivora\tecnologia\nivora-backend
mvnw.cmd clean verify
```

Las pruebas usan H2 y fuentes ficticias; no requieren arrancar Empresa de Agua.
Con MySQL encendido, abrir su cliente en otro CMD:

```bat
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p
```

Desde `mysql>`, revisar el recuento y aplicar la migración:

```sql
SELECT COUNT(*) AS lecturas_antes FROM nivora.lecturas;
SOURCE C:/Users/HP/Desktop/Nivora/tecnologia/nivora-backend/base-datos/21-lecturas-intervalo.sql;
SHOW COLUMNS FROM nivora.lecturas LIKE 'inicio_intervalo_utc';
SELECT COUNT(*) AS lecturas_despues FROM nivora.lecturas;
```

Los recuentos deben coincidir mientras Nivora esté detenido. El script 03
incluye el esquema para instalaciones nuevas; el 21 actualiza una base
anterior mediante un único ALTER y reconoce las instalaciones ya migradas.
Ejecutar el 21 con la cuenta administradora, antes de arrancar el JAR nuevo.
El preparador de carga también incluye la migración 21 en sus bases temporales.

Para repetir únicamente los casos específicos:

```bat
mvnw.cmd "-Dtest=ConsumoIntervalosTests,LecturasIntervaloJdbcTests,FuenteLecturasHttpTests,RecuperacionConCorteTests,AccesoConsultaIntegradoTests#intervalos*,DocumentacionApiTests" test
```

La recepción de datos de intervalo utiliza la integración con la fuente;
no se agrega una ruta pública para insertar lecturas desde una sesión de usuario.
