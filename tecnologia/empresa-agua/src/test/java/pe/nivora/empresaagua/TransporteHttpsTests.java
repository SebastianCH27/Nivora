package pe.nivora.empresaagua;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Comparator;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class TransporteHttpsTests {
    @Test void elPerfilHttpsSirveLaApiRealConCertificadoValidadoYSinAlterarSuContrato() throws Exception {
        Path temporal = Files.createTempDirectory("empresa-agua-tls-");
        Path almacen = temporal.resolve("localhost.p12");
        String clave = "solo-prueba-no-produccion";
        try {
            String ejecutable = System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool";
            var generacion = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", ejecutable).toString(),
                "-genkeypair", "-alias", "localhost", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-storetype", "PKCS12",
                "-keystore", almacen.toString(), "-storepass", clave, "-keypass", clave, "-noprompt")
                .redirectErrorStream(true).redirectOutput(temporal.resolve("keytool.log").toFile()).start();
            assertTrue(generacion.waitFor(30, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0, generacion.exitValue());
            var llaves = KeyStore.getInstance("PKCS12");
            try (var entrada = Files.newInputStream(almacen)) { llaves.load(entrada, clave.toCharArray()); }
            var confianza = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            confianza.init(llaves);
            var tls = SSLContext.getInstance("TLS"); tls.init(null, confianza.getTrustManagers(), null);
            try (var contexto = new SpringApplicationBuilder(EmpresaAguaApplication.class).profiles("test", "https")
                    .run("--server.port=0", "--server.ssl.key-store=" + almacen.toUri(), "--server.ssl.key-store-password=" + clave,
                        "--spring.datasource.url=jdbc:h2:mem:empresa_https;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
                    var http = HttpClient.newBuilder().sslContext(tls).build()) {
                var jdbc = contexto.getBean(JdbcTemplate.class);
                jdbc.execute("CREATE TABLE titulares (id_titular BIGINT PRIMARY KEY, nombre_completo VARCHAR(160))");
                jdbc.execute("CREATE TABLE suministros (id_suministro BIGINT PRIMARY KEY, codigo VARCHAR(20) UNIQUE, id_titular BIGINT)");
                jdbc.execute("INSERT INTO titulares VALUES (1, 'Titular ficticio')");
                jdbc.execute("INSERT INTO suministros VALUES (1, '000101', 1)");
                String origen = "https://localhost:" + contexto.getEnvironment().getProperty("local.server.port");
                var suministro = http.send(HttpRequest.newBuilder(URI.create(origen + "/api/v1/suministros/000101")).build(),
                    HttpResponse.BodyHandlers.ofString());
                assertEquals(200, suministro.statusCode(), suministro.body());
                assertEquals("000101", new JsonMapper().readTree(suministro.body()).path("codigoSuministro").stringValue());
                assertTrue(java.util.Set.of("TLSv1.2", "TLSv1.3").contains(suministro.sslSession().orElseThrow().getProtocol()));
                var ausente = http.send(HttpRequest.newBuilder(URI.create(origen + "/api/v1/suministros/000102")).build(),
                    HttpResponse.BodyHandlers.ofString());
                assertEquals(404, ausente.statusCode());
                assertEquals("Suministro no encontrado", new JsonMapper().readTree(ausente.body()).path("title").stringValue());
            }
        } finally {
            try (var archivos = Files.walk(temporal)) {
                for (var archivo : archivos.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(archivo);
            }
        }
    }
}
