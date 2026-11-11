package pe.nivora.backend.acceso.infraestructura.identidad;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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

import tools.jackson.databind.json.JsonMapper;
import pe.nivora.backend.acceso.aplicacion.InicializarAdministracion;
import pe.nivora.backend.acceso.dominio.contratos.FuenteIdentidad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccesoConsultaIntegradoTests {

    private static final String CONSULTA = "/api/v1/acceso/consulta";
    private static final String CONSUMO = CONSULTA + "/consumo-actual";
    private static final String HISTORIAL = CONSULTA + "/historial";
    private static final String ADMIN = "/api/v1/acceso/administradores";
    private static final String SUPERVISION = "/api/v1/supervision/suministros/000101/lecturas";
    private static final String DNI = "00000001";
    private static final String TOKEN_PROVEEDOR = "token-ficticio-integracion";
    private static final String SOLICITUD = "{\"codigoSuministro\":\"000101\",\"dni\":\"00000001\"}";
    private static final String CLAVE_ADMIN = "Frase ficticia de administracion";
    private static final Instant AHORA = Instant.parse("2026-11-11T13:00:00Z");
    private static final List<String> SCRIPTS = List.of("02-suministros.sql", "03-lecturas.sql",
        "04-recuperacion-lecturas.sql", "05-contexto-recuperacion-lecturas.sql",
        "06-sincronizacion-lecturas.sql", "07-administradores.sql",
        "08-sesiones-administrativas.sql", "09-sesiones-consulta.sql");

    private static final Respuesta AGUA_OK = new Respuesta(200, "application/json",
        "{\"codigoSuministro\":\"000101\",\"titular\":\"Titular ficticio\"}");
    private static final Respuesta IDENTIDAD_OK = new Respuesta(200, "application/json",
        "{\"dni\":\"00000001\",\"nombres\":\"Persona\",\"apellidoPaterno\":\"Ficticia\"}");
    private static final Respuesta CAIDA = new Respuesta(503, "application/json", "{}");
    private static final AtomicReference<Respuesta> agua = new AtomicReference<>(AGUA_OK);
    private static final AtomicReference<Respuesta> identidad = new AtomicReference<>(IDENTIDAD_OK);
    private static final AtomicInteger llamadasAgua = new AtomicInteger();
    private static final AtomicInteger llamadasIdentidad = new AtomicInteger();
    private static final AtomicReference<String> peticionAgua = new AtomicReference<>();
    private static final AtomicReference<PeticionIdentidad> peticionIdentidad = new AtomicReference<>();
    private static final HttpServer fuentes = iniciarFuentes();

    @Autowired private DataSource datos;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JsonMapper json;
    @Autowired private InicializarAdministracion inicializar;
    @Value("${local.server.port}") private int puerto;

    private Connection esquema;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> "jdbc:h2:mem:nivora_acceso_integrado;MODE=MySQL;"
            + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS nivora\\;SET SCHEMA nivora");
        registro.add("nivora.empresa-agua.url-base", () -> origen().toString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionPrueba {
        @Bean @Primary FuenteIdentidad fuenteIdentidadDePrueba(JsonMapper json) {
            return new FuenteIdentidadApisPeru(origen(), TOKEN_PROVEEDOR, json);
        }

        @Bean @Primary Clock relojDePrueba() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }
    }

    @BeforeEach
    void preparar() throws Exception {
        if (esquema == null) {
            esquema = datos.getConnection();
        }
        var scripts = SCRIPTS.stream().map(s -> new FileSystemResource("base-datos/" + s))
            .toArray(FileSystemResource[]::new);
        new ResourceDatabasePopulator(scripts).populate(esquema);
        for (String tabla : List.of("sesiones_consulta", "limites_acceso_consulta",
                "sesiones_administrativas", "limites_acceso_administrativo",
                "auditoria_administradores", "administradores", "lecturas",
                "contexto_recuperacion_lecturas", "progreso_recuperacion_lecturas",
                "programacion_sincronizacion_lecturas", "suministros")) {
            jdbc.execute("DELETE FROM " + tabla);
        }
        agua.set(AGUA_OK);
        identidad.set(IDENTIDAD_OK);
        llamadasAgua.set(0);
        llamadasIdentidad.set(0);
        peticionAgua.set(null);
        peticionIdentidad.set(null);
    }

    @AfterAll
    void cerrar() throws Exception {
        fuentes.stop(0);
        if (esquema != null) {
            esquema.close();
        }
        http.close();
    }

    @Test
    void consultaAmbasFuentesHttpConLosDatosCorrectosYRegistraElAcceso() throws Exception {
        var respuesta = enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201);
        var inicio = json.readTree(respuesta.body());
        var sesion = inicio.path("sesion");
        assertEquals("CONSULTA", sesion.path("rol").stringValue());
        assertEquals("Persona Ficticia", sesion.path("nombre").stringValue());
        assertEquals("000101", sesion.path("codigoSuministro").stringValue());
        assertEquals("GET /api/v1/suministros/000101", peticionAgua.get());
        assertEquals(new PeticionIdentidad("GET", "/api/v1/dni/" + DNI, null, "Bearer " + TOKEN_PROVEEDOR),
            peticionIdentidad.get());
        assertEquals(1, llamadasAgua.get());
        assertEquals(1, llamadasIdentidad.get());
        assertEquals(DNI, jdbc.queryForObject("SELECT dni_utilizado FROM sesiones_consulta", String.class));
        assertEquals("Persona Ficticia", jdbc.queryForObject(
            "SELECT nombre_consultado FROM sesiones_consulta", String.class));
        assertFalse(respuesta.body().contains(DNI));
        assertFalse(respuesta.body().contains(TOKEN_PROVEEDOR));
    }

    @Test
    void distingueSuministroAusenteDeUnaRutaExternaIncorrecta() throws Exception {
        agua.set(new Respuesta(404, "application/problem+json",
            "{\"status\":404,\"title\":\"Suministro no encontrado\","
                + "\"instance\":\"/api/v1/suministros/000101\"}"));
        enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 401);
        agua.set(new Respuesta(404, "application/problem+json",
            "{\"status\":404,\"title\":\"Not Found\",\"instance\":\"/otra-ruta\"}"));
        enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 503);
        assertEquals(2, llamadasAgua.get());
        assertEquals(0, llamadasIdentidad.get());
        assertSinAccesos();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 503})
    void losFallosDeIdentidadInterrumpenElInicioSinExponerDatos(int estado) throws Exception {
        identidad.set(new Respuesta(estado, "application/json",
            "{\"message\":\"" + DNI + " " + TOKEN_PROVEEDOR + "\"}"));
        var respuesta = enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 503);
        assertEquals(1, llamadasIdentidad.get());
        assertFalse(respuesta.body().contains(DNI));
        assertFalse(respuesta.body().contains(TOKEN_PROVEEDOR));
        assertSinAccesos();
    }

    @Test
    void unHttp200SinIdentidadConfirmadaTampocoCreaSesion() throws Exception {
        identidad.set(new Respuesta(200, "application/json",
            "{\"success\":false,\"message\":\"Sin resultado\"}"));
        enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 503);
        assertSinAccesos();
    }

    @Test
    void unaCaidaExternaConservaSesionesYPermisosYElCierreRevocaElToken() throws Exception {
        String consulta = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        agua.set(CAIDA);
        identidad.set(CAIDA);
        enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 503);

        inicializar.ejecutar("admin", "Admin de prueba", CLAVE_ADMIN.toCharArray());
        String admin = token(enviar("POST", ADMIN + "/sesiones",
            "{\"usuario\":\"admin\",\"clave\":\"" + CLAVE_ADMIN + "\"}", null, 201));
        enviar("GET", CONSULTA + "/sesion", null, consulta, 200);
        enviar("GET", CONSULTA + "/sesion", null, admin, 401);
        enviar("GET", SUPERVISION, null, consulta, 401);
        enviar("GET", SUPERVISION, null, admin, 200);
        enviar("GET", CONSUMO, null, admin, 401);
        enviar("GET", HISTORIAL, null, admin, 401);
        enviar("DELETE", CONSULTA + "/sesion", null, consulta, 204);
        enviar("GET", CONSULTA + "/sesion", null, consulta, 401);
        assertEquals(2, llamadasAgua.get());
        assertEquals(1, llamadasIdentidad.get());
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM sesiones_consulta WHERE revocada_en_utc IS NOT NULL", Integer.class));
    }

    @Test
    void consultaSoloElConsumoDelSuministroDeLaSesionConLasFuentesCaidas() throws Exception {
        enviar("GET", CONSUMO, null, null, 401);
        String sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        agua.set(CAIDA);
        identidad.set(CAIDA);
        jdbc.update("INSERT INTO suministros (codigo, titular, verificado_en_utc) VALUES (?, ?, ?)",
            "000102", "Otro titular ficticio", LocalDateTime.parse("2026-11-01T00:00:00"));
        lectura("empresa-agua", "1", "000101", "2026-11-01T04:59:59.999999", 50);
        lectura("empresa-agua", "2", "000101", "2026-11-01T05:00:00", 100);
        lectura("empresa-agua", "3", "000101", "2026-11-03T05:00:00", 1350);
        lectura("empresa-agua", "4", "000101", "2026-11-11T13:00:00.000001", 9000);
        lectura("empresa-agua", "5", "000102", "2026-11-10T05:00:00", 10000);
        lectura("otra-fuente", "6", "000101", "2026-11-05T05:00:00", 5000);

        var respuesta = enviar("GET", CONSUMO + "?codigoSuministro=000102", null, sesion, 200);
        var cuerpo = json.readTree(respuesta.body());
        var consumo = cuerpo.path("consumo");
        assertEquals("000101", cuerpo.path("codigoSuministro").stringValue());
        assertEquals("2026-11", cuerpo.path("periodo").stringValue());
        assertEquals("America/Lima", cuerpo.path("zonaHoraria").stringValue());
        assertEquals("2026-11-01T05:00:00Z", cuerpo.path("inicioMes").stringValue());
        assertEquals("2026-12-01T05:00:00Z", cuerpo.path("finMesExclusivo").stringValue());
        assertEquals("COMPLETO", consumo.path("estado").stringValue());
        assertEquals("1250", consumo.path("litros").stringValue());
        assertEquals("1.250", consumo.path("metrosCubicos").stringValue());
        assertEquals("2", consumo.path("idLecturaInicial").stringValue());
        assertEquals("3", consumo.path("idLecturaFinal").stringValue());
        assertEquals("2026-11-03T05:00:00Z", cuerpo.at("/actualizacion/ultimaLectura").stringValue());
        assertEquals("PENDIENTE", cuerpo.at("/actualizacion/estado").stringValue());
        assertFalse(respuesta.body().contains(DNI));
        assertFalse(respuesta.body().contains("Persona Ficticia"));
        assertFalse(respuesta.body().contains(sesion));
        assertEquals(1, llamadasAgua.get());
        assertEquals(1, llamadasIdentidad.get());
        enviar("DELETE", CONSULTA + "/sesion", null, sesion, 204);
        enviar("GET", CONSUMO, null, sesion, 401);
    }

    @Test
    void mantieneLaFechaDeLaUltimaLecturaCuandoElMesAunNoTieneDatos() throws Exception {
        String sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        var vacio = json.readTree(enviar("GET", CONSUMO, null, sesion, 200).body());
        assertEquals("SIN_LECTURAS", vacio.at("/actualizacion/estado").stringValue());
        assertTrue(vacio.at("/actualizacion/ultimaLectura").isNull());

        lectura("empresa-agua", "1", "000101", "2026-10-31T23:00:00", 50);
        var anterior = json.readTree(enviar("GET", CONSUMO, null, sesion, 200).body());
        assertEquals("SIN_DATOS", anterior.at("/consumo/estado").stringValue());
        assertTrue(anterior.at("/consumo/litros").isNull());
        assertEquals("2026-10-31T23:00:00Z", anterior.at("/actualizacion/ultimaLectura").stringValue());

        lectura("empresa-agua", "2", "000101", "2026-11-02T05:00:00", 100);
        lectura("empresa-agua", "3", "000101", "2026-11-03T05:00:00", 110);
        var parcial = json.readTree(enviar("GET", CONSUMO, null, sesion, 200).body());
        assertEquals("PARCIAL", parcial.at("/consumo/estado").stringValue());
        assertEquals("FALTA_LECTURA_INICIAL", parcial.at("/consumo/motivo").stringValue());
        assertEquals("10", parcial.at("/consumo/litros").stringValue());
    }

    @Test
    void diferenciaElCorteCompletoDeUnFalloDeActualizacion() throws Exception {
        String sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        lectura("empresa-agua", "1", "000101", "2026-11-01T05:00:00", 100);
        lectura("empresa-agua", "2", "000101", "2026-11-03T05:00:00", 120);
        LocalDateTime corte = LocalDateTime.parse("2026-11-10T05:00:00");
        jdbc.update("""
            INSERT INTO progreso_recuperacion_lecturas
                (fuente, codigo_suministro, cursor_confirmado, revision, estado, actualizado_en_utc)
            VALUES ('empresa-agua', '000101', '2', 1, 'RECORRIDO_COMPLETO', ?)
            """, corte);
        jdbc.update("""
            INSERT INTO contexto_recuperacion_lecturas
                (fuente, codigo_suministro, hasta_id_lectura, confirmado_hasta_utc,
                    inventario_json, capturado_en_utc)
            VALUES ('empresa-agua', '000101', '5', ?, '[]', ?)
            """, corte, corte);
        var confirmado = json.readTree(enviar("GET", CONSUMO, null, sesion, 200).body());
        assertEquals("CORTE_CONFIRMADO", confirmado.at("/actualizacion/estado").stringValue());
        assertEquals("2026-11-10T05:00:00Z", confirmado.at("/actualizacion/confirmadoHasta").stringValue());

        jdbc.update("UPDATE progreso_recuperacion_lecturas SET estado = 'ERROR_FUENTE'");
        var fallo = json.readTree(enviar("GET", CONSUMO, null, sesion, 200).body());
        assertEquals("CON_INCIDENCIAS", fallo.at("/actualizacion/estado").stringValue());
        assertTrue(fallo.at("/actualizacion/confirmadoHasta").isNull());
        assertEquals(confirmado.path("consumo"), fallo.path("consumo"));
    }

    @Test
    void historialEntregaLosMesesAnterioresConCoberturaYRespetaElSuministroDeLaSesion() throws Exception {
        enviar("GET", HISTORIAL, null, null, 401);
        String sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        agua.set(CAIDA);
        identidad.set(CAIDA);
        jdbc.update("INSERT INTO suministros (codigo, titular, verificado_en_utc) VALUES (?, ?, ?)",
            "000102", "Otro titular ficticio", LocalDateTime.parse("2026-11-01T00:00:00"));
        lectura("empresa-agua", "1", "000101", "2026-06-01T05:00:00", 50);
        lectura("empresa-agua", "2", "000101", "2026-06-15T05:00:00", 40);
        lectura("empresa-agua", "3", "000101", "2026-07-01T05:00:00", 100);
        lectura("empresa-agua", "4", "000101", "2026-07-22T05:00:00", 300);
        lectura("empresa-agua", "5", "000101", "2026-09-02T05:00:00", 500);
        lectura("empresa-agua", "6", "000101", "2026-10-01T05:00:00", 800);
        lectura("empresa-agua", "7", "000101", "2026-11-01T05:00:00", 2050);
        lectura("empresa-agua", "8", "000101", "2026-11-01T05:00:00.000001", 9000);
        lectura("empresa-agua", "9", "000102", "2026-10-10T05:00:00", 10000);
        lectura("otra-fuente", "10", "000101", "2026-10-12T05:00:00", 11000);

        var respuesta = enviar("GET", HISTORIAL + "?meses=5&codigoSuministro=000102", null, sesion, 200);
        var cuerpo = json.readTree(respuesta.body());
        assertEquals("000101", cuerpo.path("codigoSuministro").stringValue());
        assertEquals("America/Lima", cuerpo.path("zonaHoraria").stringValue());
        var periodos = cuerpo.path("periodos");
        assertEquals(5, periodos.size());
        var octubre = periodos.get(0);
        assertEquals("2026-10", octubre.path("periodo").stringValue());
        assertEquals("2026-10-01T05:00:00Z", octubre.path("inicioMes").stringValue());
        assertEquals("2026-11-01T05:00:00Z", octubre.path("finMesExclusivo").stringValue());
        assertEquals("COMPLETO", octubre.at("/consumo/estado").stringValue());
        assertEquals("1250", octubre.at("/consumo/litros").stringValue());
        assertEquals("1.250", octubre.at("/consumo/metrosCubicos").stringValue());
        assertEquals("6", octubre.at("/consumo/idLecturaInicial").stringValue());
        assertEquals("7", octubre.at("/consumo/idLecturaFinal").stringValue());
        var septiembre = periodos.get(1);
        assertEquals("2026-09", septiembre.path("periodo").stringValue());
        assertEquals("PARCIAL", septiembre.at("/consumo/estado").stringValue());
        assertEquals("FALTA_LECTURA_INICIAL", septiembre.at("/consumo/motivo").stringValue());
        assertEquals("6", septiembre.at("/consumo/idLecturaFinal").stringValue());
        assertEquals("2026-08", periodos.get(2).path("periodo").stringValue());
        assertEquals("SIN_DATOS", periodos.get(2).at("/consumo/estado").stringValue());
        assertTrue(periodos.get(2).at("/consumo/litros").isNull());
        assertEquals("FALTA_LECTURA_FINAL", periodos.get(3).at("/consumo/motivo").stringValue());
        assertEquals("200", periodos.get(3).at("/consumo/litros").stringValue());
        assertEquals("INCOMPATIBLE", periodos.get(4).at("/consumo/estado").stringValue());
        assertEquals("RETROCESO_DEL_CONTADOR", periodos.get(4).at("/consumo/motivo").stringValue());
        assertTrue(periodos.get(4).at("/consumo/litros").isNull());

        assertFalse(respuesta.body().contains(DNI));
        assertFalse(respuesta.body().contains("Persona Ficticia"));
        assertFalse(respuesta.body().contains(sesion));
        assertEquals(1, llamadasAgua.get());
        assertEquals(1, llamadasIdentidad.get());
        enviar("DELETE", CONSULTA + "/sesion", null, sesion, 204);
        enviar("GET", HISTORIAL, null, sesion, 401);
    }

    @Test
    void historialAdmiteUnaVentanaAnteriorYSinLecturas() throws Exception {
        String sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        var reciente = json.readTree(enviar("GET", HISTORIAL, null, sesion, 200).body()).path("periodos");
        assertEquals(12, reciente.size());
        assertEquals("2026-10", reciente.get(0).path("periodo").stringValue());
        assertEquals("2025-11", reciente.get(11).path("periodo").stringValue());

        var anterior = json.readTree(enviar("GET", HISTORIAL + "?meses=2&hasta=2024-03",
            null, sesion, 200).body()).path("periodos");
        assertEquals(2, anterior.size());
        assertEquals("2024-03", anterior.get(0).path("periodo").stringValue());
        assertEquals("2024-02", anterior.get(1).path("periodo").stringValue());
        assertEquals("2024-03-01T05:00:00Z", anterior.get(1).path("finMesExclusivo").stringValue());
        assertEquals("SIN_DATOS", anterior.get(1).at("/consumo/estado").stringValue());
        assertTrue(anterior.get(1).at("/consumo/metrosCubicos").isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"meses=0", "meses=13", "meses=texto", "hasta=2026-11",
        "hasta=2026-12", "hasta=2026-9", "hasta=2026-13", "meses=12&hasta=1000-01"})
    void historialRechazaRangosInvalidos(String consulta) throws Exception {
        String sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        var respuesta = enviar("GET", HISTORIAL + "?" + consulta, null, sesion, 400);
        assertEquals("Rango de historial invalido", json.readTree(respuesta.body()).path("title").stringValue());
    }

    private void lectura(String fuente, String id, String codigo, String fecha, long litros) {
        LocalDateTime medida = LocalDateTime.parse(fecha);
        jdbc.update("""
            INSERT INTO lecturas (fuente, id_lectura, codigo_suministro, id_medidor,
                fecha_medicion_utc, valor_litros, recibida_en_utc)
            VALUES (?, ?, ?, '1', ?, ?, ?)
            """, fuente, id, codigo, medida, litros, medida);
    }

    private void assertSinAccesos() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sesiones_consulta", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM suministros", Integer.class));
    }

    private String token(HttpResponse<String> respuesta) {
        return json.readTree(respuesta.body()).path("token").stringValue();
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String cuerpo, String token, int estado)
            throws Exception {
        var peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta))
            .timeout(Duration.ofSeconds(10));
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        if (cuerpo != null) {
            peticion.header("Content-Type", "application/json");
        }
        var respuesta = http.send(peticion.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(cuerpo)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(estado, respuesta.statusCode(), respuesta.body());
        assertTrue(respuesta.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        return respuesta;
    }

    private static URI origen() {
        return URI.create("http://127.0.0.1:" + fuentes.getAddress().getPort());
    }

    private static HttpServer iniciarFuentes() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/api/v1/suministros/", intercambio -> {
                llamadasAgua.incrementAndGet();
                peticionAgua.set(intercambio.getRequestMethod() + " " + intercambio.getRequestURI());
                responder(intercambio, agua.get());
            });
            servidor.createContext("/api/v1/dni/", intercambio -> {
                llamadasIdentidad.incrementAndGet();
                peticionIdentidad.set(new PeticionIdentidad(intercambio.getRequestMethod(),
                    intercambio.getRequestURI().getRawPath(), intercambio.getRequestURI().getRawQuery(),
                    intercambio.getRequestHeaders().getFirst("Authorization")));
                responder(intercambio, identidad.get());
            });
            servidor.start();
            return servidor;
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo iniciar el servidor HTTP de pruebas.", error);
        }
    }

    private static void responder(HttpExchange intercambio, Respuesta respuesta) throws IOException {
        try (intercambio) {
            byte[] bytes = respuesta.cuerpo().getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().set("Content-Type", respuesta.tipo());
            intercambio.sendResponseHeaders(respuesta.estado(), bytes.length);
            intercambio.getResponseBody().write(bytes);
        }
    }

    private record Respuesta(int estado, String tipo, String cuerpo) {}

    private record PeticionIdentidad(String metodo, String ruta, String consulta, String autorizacion) {}
}
