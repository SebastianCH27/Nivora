# Caso de estudio: Nivora

## Descripción

Nivora es un sistema para el monitoreo del consumo de agua potable,
accesible desde celulares, laptops, computadoras y otros dispositivos.

Permitirá conocer el consumo durante el mes, anticipar el gasto,
consultar recibos y recibir notificaciones relacionadas con el suministro.

## Problema

Las personas normalmente conocen el importe definitivo de su consumo
cuando reciben el recibo mensual. Durante el mes tienen poca información
para anticipar ese gasto o reconocer un aumento de consumo.

Además, la entrega física del recibo dificulta disponer de esa información
de manera inmediata desde distintos lugares.

Nivora busca facilitar el seguimiento del consumo y la consulta digital
de los recibos, reduciendo la dependencia de su entrega casa por casa.

## Acceso al sistema

La persona ingresará dos datos:

- Código de suministro del hogar.
- DNI de la persona que realiza la consulta.

El sistema comprobará que los campos estén completos y tengan el formato
requerido. También verificará que el código corresponda a un suministro
existente y consultará una API de identidad para validar el DNI
y obtener los datos asociados al documento.

Si los datos son incorrectos o inexistentes, se mostrará un mensaje
de error y no se permitirá ingresar. Si un servicio de validación
no responde, se informará que no fue posible completar la comprobación.

Cuando las validaciones sean satisfactorias, se iniciará una sesión
asociada al suministro consultado. Se registrarán el código de suministro,
el DNI utilizado, los datos obtenidos y la fecha y hora del acceso.

Cualquier persona podrá consultar un suministro si ingresa su código
existente y un DNI válido, sin exigir que el documento pertenezca al titular.

## Información disponible

La persona podrá consultar:

- Consumo acumulado del mes en litros y su equivalente en metros cúbicos.
- Fecha y hora de la última lectura disponible.
- Costo acumulado estimado según las tarifas aplicables.
- Proyección del consumo y costo al finalizar el mes si mantiene
  el ritmo promedio registrado.
- Historial de consumo de meses anteriores.
- Gráficos y estadísticas para comparar periodos.
- Alertas cuando el consumo acumulado del mes actual supere
  el consumo total del mes anterior.
- Recibos mensuales disponibles y sus fechas límite de pago.

Los costos acumulados y las proyecciones se identificarán como estimaciones.
El importe definitivo corresponderá al recibo proporcionado por la empresa.

## Recibos y notificaciones

Al finalizar cada mes, cuando el recibo esté disponible, Nivora enviará
una notificación indicando el mes correspondiente y su fecha límite de pago.

Para la demostración del proyecto, se considerará un plazo de una semana
desde la disponibilidad del recibo.

Las alertas de consumo y los avisos de recibos llegarán a los dispositivos
asociados al suministro, incluso cuando Nivora esté cerrada,
siempre que tengan habilitado el permiso de notificación.

Los avisos también podrán consultarse dentro de la aplicación.

## Sistemas externos e integración

En el escenario futuro, los medidores inteligentes enviarán sus lecturas
al sistema de la empresa prestadora de agua.

La empresa proporcionará mediante una API la información necesaria
de suministros, lecturas, tarifas y recibos. Nivora incorporará esos datos
para actualizar sus consultas, cálculos y alertas.

Una API de identidad permitirá consultar los datos asociados al DNI
ingresado durante el acceso.

Nivora utilizará una base de datos propia para conservar las lecturas,
los resultados, los registros de acceso, los recibos y las notificaciones.

## Alcance de esta primera etapa

Se desarrollará la aplicación Nivora y se utilizará una API simulada
que represente al sistema de la empresa de agua.

La simulación proporcionará suministros, lecturas, tarifas y recibos,
incluyendo escenarios de consumo normal, consumo elevado,
datos inválidos y fallos de comunicación.

Se comprobará mediante pruebas la capacidad de atender 5000 usuarios
simultáneamente mientras se actualizan lecturas y se evalúan alertas.

El administrador dispondrá de un acceso diferenciado para gestionar
configuraciones y supervisar el funcionamiento de la integración.

La fabricación e instalación de medidores, los sistemas internos
de la empresa, la integración real y el procesamiento de pagos
quedan fuera de esta primera etapa.

El monitoreo de electricidad, las metas personales, las recomendaciones
y los reportes descargables podrán incorporarse posteriormente.