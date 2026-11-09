package pe.nivora.empresaagua.api;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    properties = "spring.datasource.url=jdbc:h2:mem:empresa_lecturas_api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
@ActiveProfiles("test")
class LecturasApiTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    @Value("${local.server.port}")
    private int puerto;

    @BeforeEach
    void prepararLecturasAisladas() {
        jdbc.execute("DROP TABLE IF EXISTS lecturas");
        jdbc.execute("DROP TABLE IF EXISTS medidores");
        jdbc.execute("DROP TABLE IF EXISTS suministros");
        jdbc.execute("""
            CREATE TABLE suministros (
                id_suministro BIGINT PRIMARY KEY,
                codigo VARCHAR(20) NOT NULL UNIQUE
            )
            """);
        jdbc.execute("""
            CREATE TABLE medidores (
                id_medidor BIGINT PRIMARY KEY,
                id_suministro BIGINT NOT NULL REFERENCES suministros(id_suministro),
                UNIQUE (id_medidor, id_suministro)
            )
            """);
        jdbc.execute("""
            CREATE TABLE lecturas (
                id_lectura BIGINT PRIMARY KEY,
                id_suministro BIGINT NOT NULL,
                id_medidor BIGINT NOT NULL,
                fecha_medicion DATETIME(6) NOT NULL,
                lectura_acumulada_m3 DECIMAL(15,3) NOT NULL,
                FOREIGN KEY (id_medidor, id_suministro)
                    REFERENCES medidores(id_medidor, id_suministro)
            )
            """);
        jdbc.execute("CREATE INDEX idx_lecturas_suministro_id ON lecturas (id_suministro, id_lectura)");
        jdbc.update("INSERT INTO suministros VALUES (1, '13100390'), (2, '13100391'), (3, '00001234')");
        jdbc.update("INSERT INTO medidores VALUES (100, 1), (101, 1), (200, 2)");
        insertar(50, 1, 101, "2026-10-03T05:00:00", "0.125");
        insertar(10, 1, 100, "2026-10-01T05:00:00", "123.456");
        insertar(30, 1, 100, "2026-10-02T05:00:00.123456", "124.001");
        insertar(20, 2, 200, "2026-10-01T05:00:00", "9.999");
    }

    @Test
    void entregaLecturasIdentificadasDelSuministroConUnidadYFechaUtc() throws Exception {
        Respuesta respuesta = consultar("13100390", "");
        assertEquals(200, respuesta.estado());
        assertEquals("no-store", respuesta.cache());
        assertTrue(respuesta.tipo().startsWith("application/json"));
        JsonNode cuerpo = respuesta.cuerpo();
        assertEquals("empresa-agua", cuerpo.get("fuente").stringValue());
        assertEquals("13100390", cuerpo.get("codigoSuministro").stringValue());
        assertEquals("ACUMULADA", cuerpo.get("tipoMedicion").stringValue());
        assertEquals("L", cuerpo.get("unidad").stringValue());
        JsonNode lecturas = cuerpo.get("lecturas");
        assertEquals(3, lecturas.size());
        assertEquals("10", lecturas.get(0).get("idLectura").stringValue());
        assertEquals("100", lecturas.get(0).get("idMedidor").stringValue());
        assertEquals(123456, lecturas.get(0).get("valorLitros").longValue());
        assertTrue(lecturas.get(0).get("valorLitros").isIntegralNumber());
        assertEquals("2026-10-02T05:00:00.123456Z",
            lecturas.get(1).get("fechaMedicion").stringValue());
        assertEquals("101", lecturas.get(2).get("idMedidor").stringValue());
        assertEquals(125, lecturas.get(2).get("valorLitros").longValue());
        assertEquals("50", cuerpo.get("siguienteDespuesDe").stringValue());
        assertFalse(cuerpo.get("hayMas").booleanValue());
    }

    @Test
    void paginaPorIdSinRepetirLaUltimaLecturaNiMezclarSuministros() throws Exception {
        JsonNode primera = consultar("13100390", "?despuesDe=0&limite=2").cuerpo();
        assertEquals(2, primera.get("lecturas").size());
        assertEquals("10", primera.get("lecturas").get(0).get("idLectura").stringValue());
        assertEquals("30", primera.get("siguienteDespuesDe").stringValue());
        assertTrue(primera.get("hayMas").booleanValue());

        JsonNode segunda = consultar("13100390", "?despuesDe=30&limite=2").cuerpo();
        assertEquals(1, segunda.get("lecturas").size());
        assertEquals("50", segunda.get("lecturas").get(0).get("idLectura").stringValue());
        assertFalse(segunda.get("hayMas").booleanValue());

        JsonNode finalizada = consultar("13100390", "?despuesDe=50&limite=2").cuerpo();
        assertEquals(0, finalizada.get("lecturas").size());
        assertEquals("50", finalizada.get("siguienteDespuesDe").stringValue());
        assertFalse(finalizada.get("hayMas").booleanValue());
    }

    @Test
    void repetirUnaConsultaConservaLosIdentificadoresYNoGeneraConsumo() throws Exception {
        JsonNode primera = consultar("13100390", "?limite=2").cuerpo();
        JsonNode repetida = consultar("13100390", "?limite=2").cuerpo();

        assertEquals(primera, repetida);
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM lecturas", Integer.class));
        assertEquals(new BigDecimal("123.456"), jdbc.queryForObject(
            "SELECT lectura_acumulada_m3 FROM lecturas WHERE id_lectura = 10", BigDecimal.class));
    }

    @Test
    void unSuministroConCerosInicialesSinLecturasDevuelveUnaPaginaVacia() throws Exception {
        Respuesta respuesta = consultar("00001234", "");
        assertEquals(200, respuesta.estado());
        assertEquals("00001234", respuesta.cuerpo().get("codigoSuministro").stringValue());
        assertEquals(0, respuesta.cuerpo().get("lecturas").size());
        assertEquals("0", respuesta.cuerpo().get("siguienteDespuesDe").stringValue());
        assertFalse(respuesta.cuerpo().get("hayMas").booleanValue());
    }

    @Test
    void unSuministroInexistenteDevuelve404() throws Exception {
        comprobarProblema(consultar("99999999", ""), 404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC", "123456789012345678901"})
    void rechazaCodigosInvalidos(String codigo) throws Exception {
        comprobarProblema(consultar(codigo, ""), 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "?despuesDe=-1", "?despuesDe=ABC", "?despuesDe=9223372036854775808",
        "?limite=0", "?limite=-1", "?limite=501", "?limite=ABC", "?limite=2147483648"
    })
    void rechazaParametrosInvalidos(String parametros) throws Exception {
        comprobarProblema(consultar("13100390", parametros), 400);
    }

    @Test
    void elLimitePredeterminadoEsCienYDetectaUnaPaginaAdicional() throws Exception {
        jdbc.update("DELETE FROM lecturas");
        for (int id = 1; id <= 101; id++) {
            insertar(id, 1, 100, "2026-10-01T05:00:00", "1.001");
        }

        JsonNode pagina = consultar("13100390", "").cuerpo();
        assertEquals(100, pagina.get("lecturas").size());
        assertEquals("100", pagina.get("siguienteDespuesDe").stringValue());
        assertTrue(pagina.get("hayMas").booleanValue());
    }

    @Test
    void aceptaElLimiteMaximo() throws Exception {
        Respuesta respuesta = consultar("13100390", "?limite=500");
        assertEquals(200, respuesta.estado());
        assertEquals(3, respuesta.cuerpo().get("lecturas").size());
    }

    @Test
    void unCursorMayorQueLosRegistrosNoSeDesborda() throws Exception {
        Respuesta respuesta = consultar("13100390", "?despuesDe=9223372036854775807");
        assertEquals(200, respuesta.estado());
        assertEquals(0, respuesta.cuerpo().get("lecturas").size());
        assertEquals("9223372036854775807",
            respuesta.cuerpo().get("siguienteDespuesDe").stringValue());
    }

    @Test
    void losIdentificadoresGrandesSeTransmitenSinPerderDigitos() throws Exception {
        long id = 9007199254740993L;
        long medidor = 9007199254740995L;
        jdbc.update("INSERT INTO medidores VALUES (?, 1)", medidor);
        insertar(id, 1, medidor, "2026-10-04T05:00:00", "0.001");

        JsonNode pagina = consultar("13100390", "?despuesDe=9007199254740992").cuerpo();
        assertEquals(Long.toString(id), pagina.get("siguienteDespuesDe").stringValue());
        JsonNode lectura = pagina.get("lecturas").get(0);
        assertTrue(lectura.get("idLectura").isString());
        assertEquals(Long.toString(id), lectura.get("idLectura").stringValue());
        assertEquals(Long.toString(medidor), lectura.get("idMedidor").stringValue());
        assertEquals(1, lectura.get("valorLitros").longValue());
    }

    @Test
    void elCursorPermiteRecuperarUnaLecturaNuevaConFechaDeMedicionAnterior() throws Exception {
        insertar(70, 1, 100, "2026-09-30T05:00:00", "122.001");

        JsonNode pagina = consultar("13100390", "?despuesDe=50").cuerpo();
        assertEquals(1, pagina.get("lecturas").size());
        assertEquals("70", pagina.get("siguienteDespuesDe").stringValue());
        assertEquals("2026-09-30T05:00:00Z",
            pagina.get("lecturas").get(0).get("fechaMedicion").stringValue());
    }

    @Test
    void unFalloDeDatosDevuelve503ConUnDetalleGenerico() throws Exception {
        jdbc.execute("DROP TABLE lecturas");

        Respuesta respuesta = consultar("13100390", "");
        comprobarProblema(respuesta, 503);
        String detalle = respuesta.cuerpo().get("detail").stringValue();
        assertFalse(detalle.contains("SELECT"));
        assertFalse(detalle.contains("jdbc:"));
    }

    @Test
    void losDatosInvalidosNoSePresentanComoUnaPaginaVacia() throws Exception {
        jdbc.update("UPDATE lecturas SET lectura_acumulada_m3 = -1 WHERE id_lectura = 10");

        comprobarProblema(consultar("13100390", ""), 503);
    }

    private void insertar(long id, long suministro, long medidor, String fecha, String valor) {
        jdbc.update("INSERT INTO lecturas VALUES (?, ?, ?, ?, ?)",
            id, suministro, medidor, LocalDateTime.parse(fecha), new BigDecimal(valor));
    }

    private void comprobarProblema(Respuesta respuesta, int estado) {
        assertEquals(estado, respuesta.estado());
        assertEquals("no-store", respuesta.cache());
        assertTrue(respuesta.tipo().startsWith("application/problem+json"));
        assertEquals(estado, respuesta.cuerpo().get("status").intValue());
    }

    private Respuesta consultar(String codigo, String parametros) throws Exception {
        try (var cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var solicitud = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + puerto
                    + "/api/v1/suministros/" + codigo + "/lecturas" + parametros))
                .timeout(Duration.ofSeconds(10)).GET().build();
            var respuesta = cliente.send(solicitud, HttpResponse.BodyHandlers.ofString());
            return new Respuesta(respuesta.statusCode(),
                respuesta.headers().firstValue("Cache-Control").orElse(""),
                respuesta.headers().firstValue("Content-Type").orElse(""),
                json.readTree(respuesta.body()));
        }
    }

    private record Respuesta(int estado, String cache, String tipo, JsonNode cuerpo) {}
}
