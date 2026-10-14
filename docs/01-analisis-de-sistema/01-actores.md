# Actores de Nivora

Los actores representan a las personas y sistemas externos que interactúan
con Nivora para consultar información, administrar la plataforma
o proporcionar servicios necesarios para su funcionamiento.

## Actores identificados

| Actor | Tipo | ¿Qué necesita realizar? |
|-------|------|-------------------------|
| Usuario de consulta | Persona | Acceder con un código de suministro existente y un DNI válido; consultar consumo, costos estimados, proyección, historial, estadísticas y recibos; revisar notificaciones y habilitar su recepción en el dispositivo. |
| Administrador | Persona | Acceder mediante un mecanismo administrativo diferenciado; consultar suministros y registros de acceso; configurar periodos, tarifas y alertas; supervisar la integración y gestionar incidencias. |
| Sistema de la empresa de agua | Sistema externo | Proporcionar información de suministros, lecturas, tarifas y recibos mediante una API. Durante esta primera etapa se representará mediante una API simulada. |
| Servicio de validación del DNI | Sistema externo | Comprobar la existencia del DNI ingresado y proporcionar los datos asociados al documento para registrar el acceso. |
| Servicio de notificaciones | Sistema externo | Recibir las solicitudes de envío de Nivora y entregar los avisos de consumo y disponibilidad de recibos a los dispositivos asociados, incluso cuando la aplicación esté cerrada. |

## Consideraciones

El usuario de consulta puede ser el titular del suministro, un familiar
u otra persona que conozca el código del hogar e ingrese un DNI válido.
Estas personas se agrupan en un mismo actor porque utilizan las mismas
funciones de consulta.

El DNI utilizado y los datos devueltos por el servicio de identidad
se conservarán en el registro de acceso.

Los medidores inteligentes forman parte del entorno externo.
Enviarán sus lecturas al sistema de la empresa de agua; Nivora obtendrá
la información mediante la API de esa empresa.

Nivora generará las alertas y decidirá cuándo enviar los avisos.
El servicio de notificaciones será responsable de su entrega
a los dispositivos que tengan habilitado el permiso correspondiente.