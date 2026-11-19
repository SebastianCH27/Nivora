package pe.nivora.backend.acceso.infraestructura.identidad;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import pe.nivora.backend.NivoraBackendApplication;
import pe.nivora.backend.compartido.infraestructura.seguridad.TransporteSeguroConfiguracion;
import static org.junit.jupiter.api.Assertions.*;

/** Despliegue real de Tomcat TLS y proveedores HTTPS; confianza explicita, sin desactivar validaciones. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransporteHttpsTests {
    private Path directorio;
    private HttpsServer proveedor;
    private ConfigurableApplicationContext contexto;
    private HttpClient cliente;
    private SSLContext tls;
    private SSLSocketFactory anterior;
    private final AtomicInteger peticiones = new AtomicInteger();
    @BeforeAll void desplegar() throws Exception {
        directorio = Files.createTempDirectory("nivora-tls-prueba-");
        Path almacen = directorio.resolve("localhost.p12");
        String clave = "solo-prueba-no-produccion";
        String ejecutable = System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool";
        var generacion = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", ejecutable).toString(),
            "-genkeypair", "-alias", "localhost", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
            "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-storetype", "PKCS12",
            "-keystore", almacen.toString(), "-storepass", clave, "-keypass", clave, "-noprompt")
            .redirectErrorStream(true).redirectOutput(directorio.resolve("keytool.log").toFile()).start();
        assertTrue(generacion.waitFor(30, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0, generacion.exitValue());
        var llaves = KeyStore.getInstance("PKCS12");
        try (var entrada = Files.newInputStream(almacen)) { llaves.load(entrada, clave.toCharArray()); }
        var claves = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        claves.init(llaves, clave.toCharArray());
        var confianza = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        confianza.init(llaves);
        tls = SSLContext.getInstance("TLS"); tls.init(claves.getKeyManagers(), confianza.getTrustManagers(), null);
        anterior = HttpsURLConnection.getDefaultSSLSocketFactory();
        HttpsURLConnection.setDefaultSSLSocketFactory(tls.getSocketFactory());
        proveedor = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proveedor.setHttpsConfigurator(new HttpsConfigurator(tls));
        EscenarioCierreBackend.publicar(proveedor, peticiones); proveedor.start();
        cliente = HttpClient.newBuilder().sslContext(tls).build();
        contexto = new SpringApplicationBuilder(NivoraBackendApplication.class, EscenarioCierreBackend.Composicion.class)
            .profiles("test", "https").run("--server.port=0", "--server.ssl.key-store=" + almacen.toUri(),
                "--server.ssl.key-store-password=" + clave,
                "--nivora.empresa-agua.url-base=https://localhost:" + proveedor.getAddress().getPort(),
                "--spring.datasource.url=jdbc:h2:mem:cierre_https;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS nivora\\;SET SCHEMA nivora");
    }
    @AfterAll void retirar() throws Exception {
        if (contexto != null) contexto.close();
        if (cliente != null) cliente.close();
        if (proveedor != null) proveedor.stop(0);
        if (anterior != null) HttpsURLConnection.setDefaultSSLSocketFactory(anterior);
        if (directorio != null) try (var archivos = Files.walk(directorio)) {
            for (var archivo : archivos.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(archivo);
        }
    }
    @Test void lasOchoConsultasPermisosRevocacionYSincronizacionFuncionanSobreHttps() throws Exception {
        var respuestas = EscenarioCierreBackend.flujo(contexto, cliente, "https");
        assertEquals(8, respuestas.size()); assertTrue(peticiones.get() >= 7);
        var salud = cliente.send(HttpRequest.newBuilder(EscenarioCierreBackend.origen(contexto, "https")
            .resolve("/actuator/health")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, salud.statusCode());
        assertTrue(salud.headers().firstValue("Strict-Transport-Security").isPresent());
    }
    @ParameterizedTest @ValueSource(strings = {"TLSv1.2", "TLSv1.3"})
    void negociaSoloProtocolosHabilitadosConCertificadoVerificado(String protocolo) throws Exception {
        var parametros = new javax.net.ssl.SSLParameters(); parametros.setProtocols(new String[]{protocolo});
        try (var http = HttpClient.newBuilder().sslContext(tls).sslParameters(parametros).build()) {
            var respuesta = http.send(HttpRequest.newBuilder(EscenarioCierreBackend.origen(contexto, "https")
                .resolve("/api/v1/acceso/consulta/sesion")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, respuesta.statusCode());
            assertEquals(protocolo, respuesta.sslSession().orElseThrow().getProtocol());
        }
    }
    @Test void unClienteSinLaConfianzaDelCertificadoRechazaLaConexion() {
        try (var sinConfianza = HttpClient.newHttpClient()) {
            assertThrows(SSLHandshakeException.class, () -> sinConfianza.send(HttpRequest.newBuilder(
                EscenarioCierreBackend.origen(contexto, "https").resolve("/actuator/health")).build(), HttpResponse.BodyHandlers.ofString()));
        }
    }
    @Test void elPuertoTlsNoExponeLaApiEnTextoPlano() throws Exception {
        URI plano = EscenarioCierreBackend.origen(contexto, "http").resolve("/actuator/health");
        try {
            var respuesta = cliente.send(HttpRequest.newBuilder(plano).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, respuesta.statusCode());
        } catch (java.io.IOException rechazoDelConector) {
            assertNotNull(rechazoDelConector);
        }
    }
    @Test void elPerfilSeguroRechazaTlsDesactivadoOProveedorHttp() {
        assertThrows(IllegalStateException.class, () -> new TransporteSeguroConfiguracion(new MockEnvironment()
            .withProperty("server.ssl.enabled", "false").withProperty("nivora.empresa-agua.url-base", "https://localhost:8444")));
        assertThrows(IllegalStateException.class, () -> new TransporteSeguroConfiguracion(new MockEnvironment()
            .withProperty("server.ssl.enabled", "true").withProperty("nivora.empresa-agua.url-base", "http://localhost:8081")));
    }
}
