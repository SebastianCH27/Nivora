package pe.nivora.empresaagua.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:empresa_suministros_api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
@ActiveProfiles("test")
class SuministrosApiTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    @Value("${local.server.port}")
    private int puerto;

    @BeforeEach
    void prepararDatosAislados() {
        jdbc.execute("DROP TABLE IF EXISTS suministros");
        jdbc.execute("DROP TABLE IF EXISTS titulares");

        jdbc.execute("""
            CREATE TABLE titulares (
                id_titular BIGINT PRIMARY KEY,
                nombre_completo VARCHAR(160) NOT NULL
            )
            """);

        jdbc.execute("""
            CREATE TABLE suministros (
                id_suministro BIGINT PRIMARY KEY,
                codigo VARCHAR(20) NOT NULL UNIQUE,
                id_titular BIGINT NOT NULL REFERENCES titulares(id_titular),
                direccion VARCHAR(200) NOT NULL
            )
            """);

        jdbc.update("INSERT INTO titulares VALUES (?, ?)",
            1L, "Titular de prueba");
        jdbc.update("INSERT INTO titulares VALUES (?, ?)",
            2L, "Otro titular de prueba");

        jdbc.update("INSERT INTO suministros VALUES (?, ?, ?, ?)",
            1L, "13100390", 1L, "Direccion ficticia 1");
        jdbc.update("INSERT INTO suministros VALUES (?, ?, ?, ?)",
            2L, "00001234", 2L, "Direccion ficticia 2");
    }

    @Test
    void consultaElSuministroSinNecesitarConsumosMensuales() throws Exception {
        Respuesta respuesta = consultar("13100390");

        assertEquals(200, respuesta.estado());
        assertEquals("no-store", respuesta.cache());
        assertTrue(respuesta.tipo().startsWith("application/json"));
        assertEquals("13100390",
            respuesta.cuerpo().get("codigoSuministro").stringValue());
        assertEquals("Titular de prueba",
            respuesta.cuerpo().get("titular").stringValue());
    }

    @Test
    void conservaLosCerosInicialesDelCodigo() throws Exception {
        Respuesta respuesta = consultar("00001234");

        assertEquals(200, respuesta.estado());
        assertEquals("00001234",
            respuesta.cuerpo().get("codigoSuministro").stringValue());
        assertEquals("Otro titular de prueba",
            respuesta.cuerpo().get("titular").stringValue());
    }

    @Test
    void distingueUnSuministroInexistente() throws Exception {
        Respuesta respuesta = consultar("99999999");

        comprobarProblema(respuesta, 404);
    }

    @Test
    void rechazaUnCodigoConLetras() throws Exception {
        comprobarProblema(consultar("ABC"), 400);
    }

    @Test
    void rechazaUnCodigoQueSuperaLaLongitudAdmitida() throws Exception {
        comprobarProblema(consultar("123456789012345678901"), 400);
    }

    @Test
    void unFalloDeDatosNoSePresentaComoSuministroInexistente() throws Exception {
        jdbc.execute("DROP TABLE suministros");

        Respuesta respuesta = consultar("13100390");

        comprobarProblema(respuesta, 503);
        String detalle = respuesta.cuerpo().get("detail").stringValue();
        assertFalse(detalle.contains("SELECT"));
        assertFalse(detalle.contains("jdbc:"));
    }

    private void comprobarProblema(Respuesta respuesta, int estado) {
        assertEquals(estado, respuesta.estado());
        assertEquals("no-store", respuesta.cache());
        assertTrue(respuesta.tipo().startsWith("application/problem+json"));
        assertEquals(estado, respuesta.cuerpo().get("status").intValue());
    }

    private Respuesta consultar(String codigo) throws Exception {
        try (var cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()) {

            var solicitud = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + puerto
                    + "/api/v1/suministros/" + codigo))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            var respuesta = cliente.send(
                solicitud, HttpResponse.BodyHandlers.ofString());

            return new Respuesta(
                respuesta.statusCode(),
                respuesta.headers().firstValue("Cache-Control").orElse(""),
                respuesta.headers().firstValue("Content-Type").orElse(""),
                json.readTree(respuesta.body()));
        }
    }

    private record Respuesta(
        int estado,
        String cache,
        String tipo,
        JsonNode cuerpo
    ) {}
}
