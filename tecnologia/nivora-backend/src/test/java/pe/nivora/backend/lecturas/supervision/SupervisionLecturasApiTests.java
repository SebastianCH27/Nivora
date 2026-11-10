package pe.nivora.backend.lecturas.supervision;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SupervisionLecturasApiTests {
    private static final String RUTA = "/api/v1/supervision/suministros/13100390/lecturas";
    private static final Instant AHORA = Instant.parse("2026-11-10T23:00:00.123456Z");
    private static final AtomicInteger CONSULTAS_EMPRESA = new AtomicInteger();
    private static final HttpServer EMPRESA = crearEmpresaNoDisponible();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource fuente;
    @Autowired private JsonMapper json;
    @Value("${local.server.port}") private int puerto;
    private Connection conexionEsquema;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3)).build();

    @TestConfiguration(proxyBeanMethods = false)
    static class RelojPrueba {
        @Bean @Primary
        Clock relojSupervision() { return Clock.fixed(AHORA, ZoneOffset.UTC); }
    }

    @DynamicPropertySource
    static void proveedor(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> "jdbc:h2:mem:nivora_supervision_api"
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
            + ";INIT=CREATE SCHEMA IF NOT EXISTS nivora\\;SET SCHEMA nivora");
        registro.add("nivora.empresa-agua.url-base",
            () -> "http://127.0.0.1:" + EMPRESA.getAddress().getPort());
    }

    private static HttpServer crearEmpresaNoDisponible() {
        try {
            var servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/", intercambio -> {
                CONSULTAS_EMPRESA.incrementAndGet();
                intercambio.sendResponseHeaders(503, -1);
                intercambio.close();
            });
            servidor.start();
            return servidor;
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    @BeforeEach
    void preparar() throws Exception {
        // H2 conserva la sesion que creo las restricciones IN de los scripts.
        if (conexionEsquema == null) conexionEsquema = fuente.getConnection();
        new ResourceDatabasePopulator(
            new FileSystemResource("base-datos/02-suministros.sql"),
            new FileSystemResource("base-datos/03-lecturas.sql"),
            new FileSystemResource("base-datos/04-recuperacion-lecturas.sql"),
            new FileSystemResource("base-datos/05-contexto-recuperacion-lecturas.sql"),
            new FileSystemResource("base-datos/06-sincronizacion-lecturas.sql"))
            .populate(conexionEsquema);
        for (String tabla : new String[]{"programacion_sincronizacion_lecturas",
                "contexto_recuperacion_lecturas", "incidencias_recuperacion_lecturas",
                "progreso_recuperacion_lecturas", "incidencias_lecturas", "lecturas",
                "control_incorporacion_lecturas", "suministros"}) {
            jdbc.execute("DELETE FROM " + tabla);
        }
        jdbc.update("INSERT INTO suministros VALUES ('13100390','Titular privado','2026-11-10 20:00:00'),('00001234','Otro titular','2026-11-10 20:00:00'),('13100391','Tercero','2026-11-10 20:00:00')");
    }

    @AfterEach
    void noConsultaLaEmpresa() { assertEquals(0, CONSULTAS_EMPRESA.get()); }

    @AfterAll
    void cerrar() throws Exception {
        EMPRESA.stop(0);
        if (conexionEsquema != null) conexionEsquema.close();
        http.close();
    }

    @Test
    void registradoSinLecturasNoInventaProgresoNiFechas() throws Exception {
        var cuerpo = get(RUTA, 200);
        assertEquals("13100390", cuerpo.get("codigoSuministro").stringValue());
        assertEquals("empresa-agua", cuerpo.get("fuente").stringValue());
        assertEquals(AHORA.toString(), cuerpo.get("consultadoEn").stringValue());
        assertFalse(cuerpo.get("sincronizacionAutomaticaHabilitada").booleanValue());
        var almacen = cuerpo.get("almacenamiento");
        assertEquals(0, almacen.get("lecturasGuardadas").longValue());
        assertTrue(almacen.get("primeraMedicion").isNull());
        assertTrue(almacen.get("ultimaMedicion").isNull());
        assertTrue(almacen.get("ultimaRecepcion").isNull());
        assertTrue(cuerpo.get("recuperacion").isNull());
        assertTrue(cuerpo.get("programacion").isNull());
        assertFalse(cuerpo.toString().contains("Titular"));
    }

    @Test
    void suministroAusenteEsAusenciaPropiaSinConsultarElProveedor() throws Exception {
        var cuerpo = get(RUTA.replace("13100390", "99999999"), 404);
        assertEquals("Suministro no registrado", cuerpo.get("title").stringValue());
        assertTrue(cuerpo.get("detail").stringValue().contains("Nivora"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC", "-1", "1.2", "123456789012345678901", "１２３", "1 OR 1=1"})
    void rechazaCodigosInvalidos(String codigo) throws Exception {
        get(RUTA.replace("13100390", URLEncoder.encode(codigo, StandardCharsets.UTF_8)), 400);
    }

    @Test
    void conservaCerosIniciales() throws Exception {
        assertEquals("00001234", get(RUTA.replace("13100390", "00001234"), 200)
            .get("codigoSuministro").stringValue());
        get(RUTA.replace("13100390", "1234"), 404);
    }

    @Test
    void resumeLecturasDeLaFuenteYSuministroSolicitados() throws Exception {
        lecturas();
        jdbc.update("INSERT INTO lecturas VALUES ('otra','50','13100390','391','2026-09-01 05:00:00',999,'L','ACUMULADA','2026-11-10 22:00:00')");
        jdbc.update("INSERT INTO lecturas VALUES ('empresa-agua','60','13100391','392','2026-12-01 05:00:00',999,'L','ACUMULADA','2026-12-01 05:00:00')");
        var almacen = get(RUTA, 200).get("almacenamiento");
        assertEquals(2, almacen.get("lecturasGuardadas").longValue());
        assertEquals("2026-10-01T05:00:00Z", almacen.get("primeraMedicion").stringValue());
        assertEquals("2026-10-02T05:00:00.123456Z", almacen.get("ultimaMedicion").stringValue());
        assertEquals("2026-11-10T21:00:00.654321Z", almacen.get("ultimaRecepcion").stringValue());
        assertFalse(almacen.has("consumoLitros"));
    }

    @Test
    void recorridoCompletoConservaIdsGrandesYFechasUtc() throws Exception {
        lecturas();
        progreso("RECORRIDO_COMPLETO");
        jdbc.update("INSERT INTO contexto_recuperacion_lecturas VALUES ('empresa-agua','13100390','9007199254740993','2026-11-10 22:00:00.123456','{}','2026-11-10 22:10:00')");
        programacion("RECORRIDO_COMPLETO", 0, "2026-11-10 23:05:00", null);
        var cuerpo = get(RUTA, 200);
        var progreso = cuerpo.get("recuperacion");
        assertEquals("RECORRIDO_COMPLETO", progreso.get("estado").stringValue());
        assertEquals("9007199254740993", progreso.get("cursorConfirmado").stringValue());
        assertEquals("9007199254740993", progreso.get("revision").stringValue());
        assertEquals("9007199254740993", progreso.get("corte").get("hastaIdLectura").stringValue());
        assertEquals("2026-11-10T22:00:00.123456Z", progreso.get("corte").get("confirmadoHasta").stringValue());
        assertEquals("2026-11-10T22:10:00Z", progreso.get("corte").get("capturadoEn").stringValue());
        assertFalse(cuerpo.get("programacion").get("reservaVigente").booleanValue());
        assertFalse(cuerpo.get("programacion").get("reservaVencida").booleanValue());
        assertTrue(cuerpo.get("programacion").get("reservaHasta").isNull());
    }

    @Test
    void falloAnteriorYReservaVigenteSeInformanPorSeparado() throws Exception {
        lecturas();
        progreso("ERROR_FUENTE");
        programacion("ERROR_FUENTE", 4, "2026-11-10 22:59:00", "2026-11-10 23:03:00");
        var cuerpo = get(RUTA, 200);
        var programa = cuerpo.get("programacion");
        assertEquals("ERROR_FUENTE", programa.get("ultimoResultado").stringValue());
        assertEquals(4, programa.get("fallosConsecutivos").intValue());
        assertTrue(programa.get("reservaVigente").booleanValue());
        assertFalse(programa.get("reservaVencida").booleanValue());
        assertFalse(cuerpo.toString().contains("token-privado"));
        assertFalse(cuerpo.toString().contains("reservaToken"));
    }

    @Test
    void reservaQueVenceExactamenteAhoraYaNoEstaVigente() throws Exception {
        programacion("ERROR_FUENTE", 2, "2026-11-10 22:00:00", "2026-11-10 23:00:00.123456");
        var programa = get(RUTA, 200).get("programacion");
        assertFalse(programa.get("reservaVigente").booleanValue());
        assertTrue(programa.get("reservaVencida").booleanValue());
    }

    @Test
    void programacionPreparadaNoInventaUnExitoNiUnRecorrido() throws Exception {
        jdbc.update("INSERT INTO programacion_sincronizacion_lecturas(fuente,codigo_suministro,proximo_intento_utc) VALUES ('empresa-agua','13100390','2026-11-10 23:05:00')");
        var cuerpo = get(RUTA, 200);
        assertTrue(cuerpo.get("recuperacion").isNull());
        var programa = cuerpo.get("programacion");
        assertTrue(programa.get("ultimoResultado").isNull());
        assertTrue(programa.get("ultimoExito").isNull());
        assertTrue(programa.get("ultimoIntento").isNull());
    }

    @Test
    void fuenteCaidaNoImpideConsultarDatosPropiosEIncidenciasAbiertas() throws Exception {
        lecturas();
        progreso("ERROR_FUENTE");
        programacion("ERROR_FUENTE", 5, "2026-11-10 23:08:00", null);
        jdbc.update("INSERT INTO incidencias_recuperacion_lecturas VALUES ('empresa-agua','13100390','ERROR_FUENTE','2026-11-10 22:00:00','2026-11-10 22:59:00',5,TRUE,NULL),('empresa-agua','13100390','SUMINISTRO_NO_ENCONTRADO','2026-11-09 22:00:00','2026-11-09 22:59:00',1,FALSE,'2026-11-10 20:00:00')");
        var cuerpo = get(RUTA, 200);
        assertEquals(2, cuerpo.get("almacenamiento").get("lecturasGuardadas").longValue());
        assertEquals(1, cuerpo.get("almacenamiento").get("incidenciasRecuperacionAbiertas").longValue());
        assertEquals("2026-11-10T22:00:00Z", cuerpo.get("programacion").get("ultimoExito").stringValue());
        assertEquals(5, cuerpo.get("programacion").get("fallosConsecutivos").intValue());
    }

    @Test
    void bloqueoSeConservaYLasConsultasNoModificanNingunaTabla() throws Exception {
        lecturas();
        progreso("BLOQUEADO");
        programacion("BLOQUEADO", 0, null, null);
        jdbc.update("INSERT INTO incidencias_lecturas(fuente,id_lectura,codigo_suministro,motivo,datos_recibidos,huella,primera_recepcion_utc,ultima_recepcion_utc,repeticiones) VALUES ('empresa-agua','51','13100390','CONTADOR_INCONSISTENTE','dato-privado',REPEAT('a',64),'2026-11-10 22:00:00','2026-11-10 22:00:00',3)");
        var antes = instantanea();
        for (int i = 0; i < 2; i++) {
            var cuerpo = get(RUTA, 200);
            assertEquals("BLOQUEADO", cuerpo.get("recuperacion").get("estado").stringValue());
            assertTrue(cuerpo.get("programacion").get("proximoIntento").isNull());
            assertEquals(1, cuerpo.get("almacenamiento").get("incidenciasLecturas").longValue());
            assertFalse(cuerpo.toString().contains("dato-privado"));
        }
        assertEquals(antes, instantanea());
    }

    @Test
    void errorDeAlmacenamientoEs503YSuRespuestaNoExponeSql() throws Exception {
        jdbc.execute("DROP TABLE programacion_sincronizacion_lecturas");
        var cuerpo = get(RUTA, 503);
        assertEquals("Supervision no disponible", cuerpo.get("title").stringValue());
        assertFalse(cuerpo.toString().contains("SELECT"));
        assertFalse(cuerpo.toString().contains("programacion_sincronizacion_lecturas"));
        assertFalse(cuerpo.toString().contains("jdbc:"));
    }

    @Test
    void validacionDelCodigoPrecedeALaConsultaDeDatos() throws Exception {
        jdbc.execute("DROP TABLE programacion_sincronizacion_lecturas");
        get(RUTA.replace("13100390", "ABC"), 400);
    }

    @Test
    void resultadoGuardadoDesconocidoEs503YSuMensajeNoSeExpone() throws Exception {
        programacion("INTERNO_PRIVADO", 0, "2026-11-10 23:05:00", null);
        var cuerpo = get(RUTA, 503);
        assertFalse(cuerpo.toString().contains("INTERNO_PRIVADO"));
    }

    @Test
    void noOfreceOperacionesDeEscrituraPorPost() throws Exception {
        var antes = instantanea();
        var respuesta = http.send(HttpRequest.newBuilder(URI.create(url(RUTA)))
            .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(405, respuesta.statusCode());
        assertEquals(antes, instantanea());
    }

    private void lecturas() {
        jdbc.update("INSERT INTO lecturas VALUES ('empresa-agua','9','13100390','391','2026-10-01 05:00:00',100,'L','ACUMULADA','2026-11-10 20:00:00'),('empresa-agua','100','13100390','391','2026-10-02 05:00:00.123456',120,'L','ACUMULADA','2026-11-10 21:00:00.654321')");
    }

    private void progreso(String estado) {
        jdbc.update("INSERT INTO progreso_recuperacion_lecturas VALUES ('empresa-agua','13100390','9007199254740993',9007199254740993,?,'2026-11-10 22:00:00','2026-11-10 22:59:00')", estado);
    }

    private void programacion(String resultado, int fallos, String proximo, String reserva) {
        jdbc.update("INSERT INTO programacion_sincronizacion_lecturas VALUES ('empresa-agua','13100390',?,?,?,?,'2026-11-10 22:59:00','2026-11-10 22:00:00',?)",
            proximo, fallos, reserva == null ? null : "token-privado", reserva, resultado);
    }

    private java.util.List<?> instantanea() {
        return java.util.List.of(jdbc.queryForList("SELECT * FROM lecturas"),
            jdbc.queryForList("SELECT * FROM incidencias_lecturas"),
            jdbc.queryForList("SELECT * FROM incidencias_recuperacion_lecturas"),
            jdbc.queryForList("SELECT * FROM progreso_recuperacion_lecturas"),
            jdbc.queryForList("SELECT * FROM contexto_recuperacion_lecturas"),
            jdbc.queryForList("SELECT * FROM programacion_sincronizacion_lecturas"),
            jdbc.queryForList("SELECT * FROM suministros"),
            jdbc.queryForList("SELECT * FROM control_incorporacion_lecturas"));
    }

    private String url(String ruta) { return "http://127.0.0.1:" + puerto + ruta; }

    private JsonNode get(String ruta, int estado) throws Exception {
        var respuesta = http.send(HttpRequest.newBuilder(URI.create(url(ruta)))
            .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(estado, respuesta.statusCode(), respuesta.body());
        assertEquals("no-store", respuesta.headers().firstValue("Cache-Control").orElse(""));
        assertTrue(respuesta.headers().firstValue("Content-Type").orElse("")
            .startsWith(estado == 200 ? "application/json" : "application/problem+json"));
        var cuerpo = json.readTree(respuesta.body());
        if (estado != 200) {
            assertEquals(estado, cuerpo.get("status").intValue());
            assertEquals(URI.create(ruta).getRawPath(), cuerpo.get("instance").stringValue());
        }
        return cuerpo;
    }
}
