package pe.nivora.empresaagua.api;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
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
    properties = "spring.datasource.url=jdbc:h2:mem:empresa_tarifas_api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
@ActiveProfiles("test")
class TarifasApiTests {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final String RUTA = "/api/v1/suministros/";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private JsonMapper json;
    @Value("${local.server.port}") private int puerto;

    @BeforeEach
    void preparar() {
        jdbc.execute("DROP TABLE IF EXISTS tramos_tarifa");
        jdbc.execute("DROP TABLE IF EXISTS suministro_tarifas");
        jdbc.execute("DROP TABLE IF EXISTS tarifas");
        jdbc.execute("DROP TABLE IF EXISTS suministros");
        jdbc.execute("DROP TABLE IF EXISTS titulares");
        jdbc.execute("CREATE TABLE titulares (id_titular BIGINT PRIMARY KEY, nombre_completo VARCHAR(160))");
        jdbc.execute("""
            CREATE TABLE suministros (
                id_suministro BIGINT PRIMARY KEY, codigo VARCHAR(20) UNIQUE,
                id_titular BIGINT NOT NULL REFERENCES titulares(id_titular))
            """);
        jdbc.execute("""
            CREATE TABLE tarifas (
                id_tarifa BIGINT PRIMARY KEY, codigo VARCHAR(30) NOT NULL,
                version INT NOT NULL, nombre VARCHAR(120) NOT NULL, cargo_fijo DECIMAL(12,2) NOT NULL,
                vigente_desde DATE NOT NULL, vigente_hasta DATE)
            """);
        jdbc.execute("""
            CREATE TABLE suministro_tarifas (
                id_asignacion BIGINT PRIMARY KEY, id_suministro BIGINT NOT NULL REFERENCES suministros(id_suministro),
                id_tarifa BIGINT NOT NULL REFERENCES tarifas(id_tarifa),
                fecha_desde DATE NOT NULL, fecha_hasta DATE)
            """);
        jdbc.execute("""
            CREATE TABLE tramos_tarifa (
                id_tramo BIGINT PRIMARY KEY, id_tarifa BIGINT NOT NULL REFERENCES tarifas(id_tarifa),
                desde_m3 DECIMAL(15,3) NOT NULL, hasta_m3 DECIMAL(15,3), precio_por_m3 DECIMAL(12,4) NOT NULL)
            """);
        jdbc.update("INSERT INTO titulares VALUES (1, 'Titular de prueba')");
        jdbc.update("INSERT INTO suministros VALUES (1,'000101',1),(2,'000102',1),(3,'000103',1)");
        jdbc.update("""
            INSERT INTO tarifas VALUES
                (1, 'DOMESTICA', 1, 'Domestica anterior', 5.00, '2026-08-01', '2026-11-01'),
                (2, 'DOMESTICA', 2, 'Domestica siguiente', 6.00, '2026-11-01', NULL),
                (3, 'OTRA', 1, 'Otro suministro', 50.00, '2020-01-01', NULL)
            """);
        jdbc.update("""
            INSERT INTO suministro_tarifas VALUES
                (2, 1, 2, '2026-11-01', NULL),
                (1, 1, 1, '2026-09-01', NULL),
                (3, 2, 3, '2020-01-01', NULL)
            """);
        jdbc.update("""
            INSERT INTO tramos_tarifa VALUES
                (2, 1, 10, NULL, 2.2543), (1, 1, 0, 10, 1.5000),
                (3, 2, 0, NULL, 2.5), (4, 3, 0, NULL, 7.5)
            """);
    }

    @AfterAll
    static void cerrar() {
        HTTP.close();
    }

    @Test
    void devuelveVersionesConLaVigenciaAplicableSinCruzarSuministros() throws Exception {
        var historial = get("000101/tarifas", 200);
        assertEquals("empresa-agua", historial.path("fuente").stringValue());
        assertEquals("000101", historial.path("codigoSuministro").stringValue());
        assertEquals("PEN", historial.path("moneda").stringValue());
        assertEquals("America/Lima", historial.path("zonaFechas").stringValue());
        assertFalse(historial.has("titular"));
        var asignaciones = historial.path("asignaciones");
        assertEquals(2, asignaciones.size());
        var anterior = asignaciones.get(0);
        assertEquals("DOMESTICA", anterior.path("codigoTarifa").stringValue());
        assertEquals(1, anterior.path("versionTarifa").intValue());
        assertEquals("2026-08-01", anterior.path("vigenteDesde").stringValue());
        assertEquals("2026-09-01", anterior.path("asignadaDesde").stringValue());
        assertTrue(anterior.path("asignadaHasta").isNull());
        assertEquals("2026-09-01", anterior.path("aplicableDesde").stringValue());
        assertEquals("2026-11-01", anterior.path("aplicableHasta").stringValue());
        assertEquals(0, new BigDecimal("5.00").compareTo(anterior.path("cargoFijo").decimalValue()));
        assertEquals(0, new BigDecimal("10").compareTo(anterior.at("/tramos/0/hastaM3").decimalValue()));
        assertEquals(0, new BigDecimal("2.2543").compareTo(anterior.at("/tramos/1/precioPorM3").decimalValue()));
        assertTrue(anterior.at("/tramos/1/hastaM3").isNull());
        assertEquals(2, asignaciones.get(1).path("versionTarifa").intValue());
        assertEquals("2026-11-01", asignaciones.get(1).path("aplicableDesde").stringValue());
        assertTrue(asignaciones.get(1).path("aplicableHasta").isNull());
    }

    @Test
    void conservaUnHuecoEntreAsignacionesYElCierreImpuestoPorLaAsignacion() throws Exception {
        jdbc.update("UPDATE suministro_tarifas SET fecha_hasta = '2026-10-01' WHERE id_asignacion = 1");
        jdbc.update("UPDATE suministro_tarifas SET fecha_desde = '2026-12-01' WHERE id_asignacion = 2");
        var asignaciones = get("000101/tarifas", 200).path("asignaciones");
        assertEquals("2026-10-01", asignaciones.get(0).path("aplicableHasta").stringValue());
        assertEquals("2026-12-01", asignaciones.get(1).path("aplicableDesde").stringValue());
    }

    @Test
    void distingueSuministroInexistenteYSuministroSinTarifas() throws Exception {
        var asignaciones = get("000103/tarifas", 200).path("asignaciones");
        assertTrue(asignaciones.isArray());
        assertEquals(0, asignaciones.size());
        get("000104/tarifas", 404);
        get("invalido/tarifas", 400);
    }

    @Test
    void rechazaTarifasSuperpuestasOAsignacionesSinVigencia() throws Exception {
        jdbc.update("UPDATE tarifas SET vigente_hasta = '2026-12-01' WHERE id_tarifa = 1");
        get("000101/tarifas", 503);
        jdbc.update("UPDATE tarifas SET vigente_hasta = '2026-08-15' WHERE id_tarifa = 1");
        get("000101/tarifas", 503);
    }

    @Test
    void rechazaTramosDiscontinuosOVersionesSinTramos() throws Exception {
        jdbc.update("UPDATE tramos_tarifa SET desde_m3 = 11 WHERE id_tramo = 2");
        get("000101/tarifas", 503);
        jdbc.update("UPDATE tramos_tarifa SET desde_m3 = 10 WHERE id_tramo = 2");
        jdbc.update("DELETE FROM tramos_tarifa WHERE id_tarifa = 2");
        get("000101/tarifas", 503);
    }

    @Test
    void tarifaActualConservaSuContrato() throws Exception {
        var tarifa = get("000102/tarifa-actual", 200);
        assertEquals("000102", tarifa.path("codigoSuministro").stringValue());
        assertEquals("OTRA", tarifa.path("codigoTarifa").stringValue());
        assertEquals("Titular de prueba", tarifa.path("titular").stringValue());
        assertEquals(1, tarifa.path("tramos").size());
    }

    private JsonNode get(String ruta, int codigoEsperado) throws Exception {
        var peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + RUTA + ruta))
            .timeout(Duration.ofSeconds(6)).GET().build();
        var respuesta = HTTP.send(peticion, HttpResponse.BodyHandlers.ofString());
        assertEquals(codigoEsperado, respuesta.statusCode(), respuesta.body());
        assertEquals("no-store", respuesta.headers().firstValue("Cache-Control").orElseThrow());
        return json.readTree(respuesta.body());
    }
}
