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
                "auditoria_administradores", "administradores", "suministros")) {
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
        enviar("DELETE", CONSULTA + "/sesion", null, consulta, 204);
        enviar("GET", CONSULTA + "/sesion", null, consulta, 401);
        assertEquals(2, llamadasAgua.get());
        assertEquals(1, llamadasIdentidad.get());
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM sesiones_consulta WHERE revocada_en_utc IS NOT NULL", Integer.class));
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
