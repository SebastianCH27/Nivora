package pe.nivora.backend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NivoraBackendApplicationTests {

    @Autowired
    private DataSource fuente;

    @Value("${local.server.port}")
    private int puerto;

    @Test
    void utilizaUnaBaseDePruebasAislada() throws Exception {
        try (var conexion = fuente.getConnection()) {
            assertTrue(
                conexion.getMetaData().getURL().startsWith("jdbc:h2:mem:"),
                "Las pruebas deben utilizar H2 en memoria.");
        }
    }

    @Test
    void respondeSaludConLaBaseDisponible() throws Exception {
        try (var cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()) {

            var solicitud = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + puerto + "/actuator/health"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            var respuesta = cliente.send(
                solicitud, HttpResponse.BodyHandlers.discarding());

            assertEquals(200, respuesta.statusCode());
        }
    }

}
