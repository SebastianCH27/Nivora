# Despliegue de pruebas con HTTPS

P04/EQ-06, comprobado el 19 de noviembre de 2026.
Ambos servicios incorporan un perfil optativo `https`. Los puertos
predeterminados del perfil son 8443 para Nivora y 8444 para la Empresa de Agua,
con enlace a loopback. Se habilitan TLS 1.2 y 1.3.

## Comprobaciones ejecutadas

`TransporteHttpsTests` del backend genera un certificado temporal con SAN
`localhost`/`127.0.0.1`, inicia Tomcat con TLS y usa un cliente que confía
explícitamente en ese certificado. Los proveedores controlados de agua e
identidad se sirven mediante HTTPS. Se mantienen las verificaciones de
confianza y nombre del servidor.

Los seis casos comprueban:

- Alta de sesión, las ocho consultas, incorporación de lecturas, sincronización
  de tarifas, recibos, alerta y aviso de recibo sin duplicación.
- Acceso sin sesión y con un tipo de sesión ajeno: 401; operación prohibida
  con una sesión reconocida: 403; revocación: 204 seguida de 401.
- Negociación explícita de TLS 1.2 y de TLS 1.3.
- Rechazo del certificado por un cliente que no tiene la confianza necesaria.
- Rechazo de HTTP en texto plano en el puerto TLS y presencia de HSTS.
- Rechazo de configuración del perfil si se desactiva TLS o se usa HTTP
  para la Empresa de Agua.

La Empresa de Agua tiene además un caso que inicia su aplicación real con
el perfil seguro y consulta suministro existente y ausente, conservando
los contratos 200/404 con el certificado verificado.

Los certificados se crean fuera del repositorio y se eliminan al terminar.
La ejecución no necesita contraseñas personales, MySQL encendido, instalar
certificados en Windows ni omitir validaciones TLS.

## Repetir en Windows

```bat
cd /d C:\Users\HP\Desktop\Nivora\tecnologia\empresa-agua
mvnw.cmd -Dtest=TransporteHttpsTests test
cd /d C:\Users\HP\Desktop\Nivora\tecnologia\nivora-backend
mvnw.cmd -Dtest=TransporteHttpsTests test
```

Ambas comprobaciones forman parte de `clean verify`.

## Configuración para otro montaje de pruebas

| Servicio | Variable | Uso |
|---|---|---|
| Nivora | `NIVORA_TLS_KEYSTORE` | URI `file:/...` de un PKCS12 válido |
| Nivora | `NIVORA_TLS_CLAVE` | Contraseña del almacén |
| Nivora | `NIVORA_EMPRESA_AGUA_URL_HTTPS` | Origen HTTPS de la empresa; por defecto `https://localhost:8444` |
| Nivora | `NIVORA_HTTPS_DIRECCION`, `NIVORA_HTTPS_PUERTO` | Dirección y puerto del conector |
| Empresa | `EMPRESA_TLS_KEYSTORE`, `EMPRESA_TLS_CLAVE` | PKCS12 y contraseña de su servidor |
| Empresa | `EMPRESA_HTTPS_DIRECCION`, `EMPRESA_HTTPS_PUERTO` | Dirección y puerto del conector |

Se activa mediante `--spring.profiles.active=https` al ejecutar el JAR.
Cada cliente debe confiar en el emisor del certificado de su servidor y
utilizar un nombre presente en su SAN. Para una CA privada de pruebas,
la JVM de Nivora puede recibir un almacén de confianza mediante
`-Djavax.net.ssl.trustStore=...` y su configuración correspondiente.
Los almacenes con claves privadas no se incorporan a Git.

El perfil conserva la conexión a MySQL existente y sus variables de entorno.
La verificación realizada cubre los conectores HTTPS y los clientes Java del
montaje local. La configuración de un dominio público, su certificado y el
acceso desde los dispositivos finales se comprueban al preparar ese despliegue.

Referencia: [configuración SSL de Spring Boot](https://docs.spring.io/spring-boot/4.1-SNAPSHOT/how-to/webserver.html).
