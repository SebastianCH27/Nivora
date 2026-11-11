package pe.nivora.backend.acceso.sesiones;

import java.net.URI;
import java.net.http.*;
import java.sql.Connection;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import javax.sql.DataSource;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.*;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import pe.nivora.backend.acceso.aplicacion.InicializarAdministracion;
import pe.nivora.backend.acceso.dominio.administradores.TokenAdministrativo;
import pe.nivora.backend.acceso.dominio.administradores.UsuarioAdministrador;
import pe.nivora.backend.acceso.dominio.administradores.PoliticaSesionAdministrativa;
import pe.nivora.backend.acceso.dominio.contratos.*;
import pe.nivora.backend.acceso.infraestructura.administradores.ProtectorClavesArgon2;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SesionesAdministrativasApiTests {
    private static final String LOGIN = "/api/v1/acceso/administradores/sesiones";
    private static final String SESION = "/api/v1/acceso/administradores/sesion";
    private static final String SUPERVISION = "/api/v1/supervision/suministros/13100390/lecturas";
    private static final String CLAVE = "Frase ficticia de administracion";
    private static final Instant AHORA = Instant.parse("2026-11-10T23:00:00.123456Z");
    private static final List<String> SCRIPTS = List.of("02-suministros.sql", "03-lecturas.sql",
        "04-recuperacion-lecturas.sql", "05-contexto-recuperacion-lecturas.sql",
        "06-sincronizacion-lecturas.sql", "07-administradores.sql", "08-sesiones-administrativas.sql");
    @Autowired private DataSource fuente;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JsonMapper json;
    @Autowired private InicializarAdministracion inicializar;
    @Autowired private RepositorioSesionesAdministrativas repositorio;
    @Autowired private PoliticaSesionAdministrativa politica;
    @Autowired private RelojMutable reloj;
    @Value("${local.server.port}") private int puerto;
    private Connection esquema;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> "jdbc:h2:mem:nivora_sesiones_api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS nivora\\;SET SCHEMA nivora");
        registro.add("nivora.empresa-agua.url-base", () -> "http://127.0.0.1:1");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionPrueba {
        @Bean @Primary RelojMutable relojPrueba() { return new RelojMutable(); }
    }
    static class RelojMutable extends Clock {
        final AtomicReference<Instant> valor = new AtomicReference<>(AHORA);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zona) { return this; }
        @Override public Instant instant() { return valor.get(); }
        void mover(long segundos) { valor.set(AHORA.plusSeconds(segundos)); }
    }

    @BeforeEach
    void preparar() throws Exception {
        reloj.mover(0);
        if (esquema == null) esquema = fuente.getConnection();
        aplicar();
        for (String tabla : List.of("sesiones_administrativas", "limites_acceso_administrativo",
                "auditoria_administradores", "administradores", "programacion_sincronizacion_lecturas",
                "contexto_recuperacion_lecturas", "incidencias_recuperacion_lecturas",
                "progreso_recuperacion_lecturas", "incidencias_lecturas", "lecturas",
                "control_incorporacion_lecturas", "suministros")) jdbc.execute("DELETE FROM " + tabla);
        inicializar.ejecutar("admin", "Responsable de prueba", CLAVE.toCharArray());
        jdbc.update("INSERT INTO suministros VALUES ('13100390','Titular privado','2026-11-10 20:00:00')");
    }

    private void aplicar() {
        var scripts = SCRIPTS.stream().map(s -> new FileSystemResource("base-datos/" + s)).toArray(FileSystemResource[]::new);
        new ResourceDatabasePopulator(scripts).populate(esquema);
    }

    @AfterAll
    void cerrar() throws Exception { if (esquema != null) esquema.close(); http.close(); }

    @Test
    void saludEsPublicaYSupervisionExigeSesionSinRedirecciones() throws Exception {
        var salud = solicitar("GET", "/actuator/health", null, null, null);
        assertEquals(200, salud.statusCode());
        var respuesta = get(SUPERVISION, null, 401);
        assertEquals("Sesion requerida", respuesta.get("title").stringValue());
        assertEquals(0, cantidad("sesiones_administrativas"));
    }

    @Test
    void inicioGeneraTokenAleatorioGuardaSoloHuellaYAudita() throws Exception {
        var respuesta = login("AdMiN", CLAVE, 201);
        String token = respuesta.get("token").stringValue();
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        assertEquals("Bearer", respuesta.get("tipoToken").stringValue());
        var datos = respuesta.get("sesion");
        assertEquals("admin", datos.get("usuario").stringValue());
        assertEquals("ADMIN", datos.get("rol").stringValue());
        assertTrue(datos.get("permisos").toString().contains("GESTIONAR_ADMINISTRADORES"));
        assertEquals(AHORA.plusSeconds(28800).toString(), datos.get("expiraEn").stringValue());
        assertEquals(AHORA.plusSeconds(900).toString(), datos.get("inactivaHasta").stringValue());
        assertFalse(respuesta.toString().contains(CLAVE));
        assertFalse(respuesta.toString().contains("hashClave"));
        assertEquals(TokenAdministrativo.interpretar(token).huella(),
            jdbc.queryForObject("SELECT hash_token FROM sesiones_administrativas", String.class));
        assertFalse(jdbc.queryForList("SELECT * FROM sesiones_administrativas").toString().contains(token));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_administradores WHERE operacion='INICIO_SESION'", Integer.class));
    }

    @Test
    void consultaSesionYSupervisionSinDevolverElToken() throws Exception {
        String token = token();
        var estado = get(SESION, token, 200);
        assertEquals("admin", estado.get("usuario").stringValue());
        assertFalse(estado.has("token"));
        assertFalse(estado.toString().contains(token));
        assertEquals(0, get(SUPERVISION, token, 200).get("almacenamiento").get("lecturasGuardadas").intValue());
    }

    @Test
    void claveIncorrectaCuentaAusenteEInactivaTienenElMismoError() throws Exception {
        var incorrecta = login("admin", "Clave incorrecta de prueba", 401);
        var ausente = login("inexistente", CLAVE, 401);
        jdbc.update("UPDATE administradores SET activa=FALSE");
        var inactiva = login("admin", CLAVE, 401);
        assertEquals(incorrecta.get("detail"), ausente.get("detail"));
        assertEquals(incorrecta.get("detail"), inactiva.get("detail"));
        assertEquals(0, cantidad("sesiones_administrativas"));
    }

    @Test
    void cuentaInicialConClaveCortaSeCompruebaEnElServidorNormal() throws Exception {
        jdbc.update("UPDATE administradores SET hash_clave=?", new ProtectorClavesArgon2(true).proteger("314159".toCharArray()));
        assertEquals("admin", login("admin", "314159", 201).get("sesion").get("usuario").stringValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]", "{\"usuario\":\"admin\"}",
        "{\"usuario\":\"admin\",\"clave\":null}", "{\"usuario\":5,\"clave\":\"valor\"}",
        "{\"usuario\":\"admin\",\"clave\":\"valor\",\"rol\":\"ADMIN\"}",
        "{\"usuario\":\"admin\",\"clave\":\"valor\"} {}",
        "{\"usuario\":\"admin\",\"usuario\":\"otro\",\"clave\":\"valor\"}", "no-es-json"})
    void jsonInvalidoNoCreaSesion(String cuerpo) throws Exception {
        comprobar(solicitar("POST", LOGIN, cuerpo, null, "application/json"), 400);
        assertEquals(0, cantidad("sesiones_administrativas"));
    }

    @Test
    void limitesDeCuerpoYTipoDeContenidoSeAplicanAntesDeAutenticar() throws Exception {
        comprobar(solicitar("POST", LOGIN, "x".repeat(4097), null, "application/json"), 413);
        comprobar(solicitar("POST", LOGIN, "texto", null, "text/plain"), 415);
        comprobar(solicitar("POST", LOGIN, "{}", null, "application/json;charset=UTF-16"), 415);
        assertEquals(0, cantidad("sesiones_administrativas"));
    }

    @Test
    void cookiesParametrosBasicYTokensMalformadosNoAutentican() throws Exception {
        String token = token();
        comprobar(solicitar("GET", SUPERVISION + "?token=" + token, null, null, null), 401);
        for (String cabecera : List.of("Bearer abc", "Basic YWRtaW46Y2xhdmU=", "Bearer ", "Bearer " + token + "x")) {
            var r = http.send(HttpRequest.newBuilder(URI.create(url(SUPERVISION)))
                .header("Authorization", cabecera).GET().build(), HttpResponse.BodyHandlers.ofString());
            comprobar(r, 401);
        }
        var cookie = http.send(HttpRequest.newBuilder(URI.create(url(SUPERVISION)))
            .header("Cookie", "JSESSIONID=" + token).GET().build(), HttpResponse.BodyHandlers.ofString());
        comprobar(cookie, 401);
        var doble = http.send(HttpRequest.newBuilder(URI.create(url(SUPERVISION)))
            .header("Authorization", "Bearer " + token).header("Authorization", "Bearer " + token)
            .GET().build(), HttpResponse.BodyHandlers.ofString());
        comprobar(doble, 401);
    }

    @Test
    void cerrarRevocaLaSesionYConservaLaDeOtroDispositivo() throws Exception {
        String uno = token(), dos = token();
        assertNotEquals(uno, dos);
        var cierre = solicitar("DELETE", SESION, null, uno, null);
        assertEquals(204, cierre.statusCode());
        assertTrue(cierre.body().isBlank());
        get(SUPERVISION, uno, 401);
        get(SUPERVISION, dos, 200);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sesiones_administrativas WHERE revocada_en_utc IS NOT NULL", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_administradores WHERE operacion='CIERRE_SESION'", Integer.class));
    }

    @Test
    void caducaExactamenteAlAlcanzarElLimiteDeInactividad() throws Exception {
        String token = token();
        reloj.mover(900);
        get(SUPERVISION, token, 401);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sesiones_administrativas WHERE revocada_en_utc IS NOT NULL", Integer.class));
    }

    @Test
    void actividadRenuevaInactividadSinAmpliarElVencimientoAbsoluto() throws Exception {
        String token = token();
        reloj.mover(899);
        var renovada = get(SESION, token, 200);
        assertEquals(AHORA.plusSeconds(1799).toString(), renovada.get("inactivaHasta").stringValue());
        assertEquals(AHORA.plusSeconds(28800).toString(), renovada.get("expiraEn").stringValue());
        reloj.mover(1000);
        get(SUPERVISION, token, 200);
    }

    @Test
    void limiteAbsolutoSeCumpleAunqueLaSesionTengaActividad() throws Exception {
        String token = token();
        jdbc.update("UPDATE sesiones_administrativas SET inactiva_hasta_utc=expira_en_utc");
        reloj.mover(28799);
        var ultima = get(SESION, token, 200);
        assertEquals(AHORA.plusSeconds(28800).toString(), ultima.get("inactivaHasta").stringValue());
        reloj.mover(28800);
        get(SUPERVISION, token, 401);
    }

    @Test
    void versionDeCredencialesInvalidaTokensPreviosYPermiteNuevoIngreso() throws Exception {
        String anterior = token();
        jdbc.update("UPDATE administradores SET version_credenciales=version_credenciales+1");
        get(SUPERVISION, anterior, 401);
        get(SUPERVISION, token(), 200);
    }

    @Test
    void desactivarLaCuentaInvalidaElAccesoDeLaSesion() throws Exception {
        String token = token();
        jdbc.update("UPDATE administradores SET activa=FALSE");
        get(SUPERVISION, token, 401);
        jdbc.update("UPDATE administradores SET activa=TRUE");
        get(SUPERVISION, token, 401);
    }

    @Test
    void permisosSeReleenYCambioDeClavePendienteRestringeLaSesion() throws Exception {
        String token = token();
        jdbc.update("UPDATE administradores SET gestionar_administradores=FALSE");
        assertFalse(get(SESION, token, 200).get("permisos").toString().contains("GESTIONAR_ADMINISTRADORES"));
        get(SUPERVISION, token, 200);
        jdbc.update("UPDATE administradores SET cambio_clave_requerido=TRUE");
        assertTrue(get(SESION, token, 200).get("cambioClaveRequerido").booleanValue());
        get(SUPERVISION, token, 403);
        assertEquals(204, solicitar("DELETE", SESION, null, token, null).statusCode());
    }

    @Test
    void limitePorUsuarioSeConservaEnBaseYCambiaAlTerminarLaVentana() throws Exception {
        for (int i = 0; i < 5; i++) login(i % 2 == 0 ? "ADMIN" : "admin", "Clave incorrecta de prueba", 401);
        var bloqueada = solicitar("POST", LOGIN, json.writeValueAsString(Map.of("usuario", "admin", "clave", CLAVE)), null, "application/json");
        comprobar(bloqueada, 429);
        assertEquals("60", bloqueada.headers().firstValue("Retry-After").orElse(""));
        assertEquals(0, cantidad("sesiones_administrativas"));
        reloj.mover(60);
        login("admin", CLAVE, 201);
    }

    @Test
    void limitePorOrigenTambienAbarcaUsuariosDesconocidosDistintos() throws Exception {
        for (int i = 0; i < 30; i++) login("ausente" + i, CLAVE, 401);
        login("ausente30", CLAVE, 429);
        assertEquals(31, cantidad("limites_acceso_administrativo"));
    }

    @Test
    void falloDeBaseDevuelve503EnLugarDeCredencialesIncorrectas() throws Exception {
        String token = token();
        jdbc.execute("DROP TABLE sesiones_administrativas");
        var protegida = get(SUPERVISION, token, 503);
        assertFalse(protegida.toString().contains("SELECT"));
        var ingreso = login("admin", CLAVE, 503);
        assertFalse(ingreso.toString().contains("sesiones_administrativas"));
        assertFalse(ingreso.toString().contains(CLAVE));
    }

    @Test
    void falloDeAuditoriaRevierteLaSesionYLaReaplicacionConservaLasCuentas() throws Exception {
        var cuenta = jdbc.queryForList("SELECT * FROM administradores");
        jdbc.execute("DROP TABLE auditoria_administradores");
        login("admin", CLAVE, 503);
        assertEquals(0, cantidad("sesiones_administrativas"));
        aplicar();
        assertEquals(cuenta, jdbc.queryForList("SELECT * FROM administradores"));
        login("admin", CLAVE, 201);
    }

    @Test
    void tokensYClavesNoAparecenEnRepresentacionesDeDiagnostico() {
        var token = TokenAdministrativo.crear();
        assertFalse(token.toString().contains(token.valor()));
        assertNotEquals(token.valor(), token.huella());
        assertEquals(64, token.huella().length());
    }

    @Test
    void falloDeAuditoriaAlCerrarNoSeInformaComoCierreExitoso() throws Exception {
        String token = token();
        jdbc.execute("DROP TABLE auditoria_administradores");
        comprobar(solicitar("DELETE", SESION, null, token, null), 503);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sesiones_administrativas WHERE revocada_en_utc IS NOT NULL", Integer.class));
        aplicar();
        get(SESION, token, 200);
        assertEquals(204, solicitar("DELETE", SESION, null, token, null).statusCode());
        get(SUPERVISION, token, 401);
    }

    @Test
    void actividadConcurrenteConCierreNoResucitaLaSesion() throws Exception {
        var token = TokenAdministrativo.interpretar(token());
        var salida = new CountDownLatch(1);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var consulta = hilos.submit(() -> { salida.await(); return repositorio.autenticar(token, AHORA, politica); });
            var cierre = hilos.submit(() -> { salida.await(); return repositorio.cerrar(token, AHORA); });
            salida.countDown();
            consulta.get();
            assertTrue(cierre.get());
        }
        assertTrue(repositorio.autenticar(token, AHORA, politica).isEmpty());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_administradores WHERE operacion='CIERRE_SESION'", Integer.class));
    }

    @Test
    void cambiosDuranteLaComprobacionDeClaveImpidenEmitirUnaSesionObsoleta() {
        var esperada = repositorio.buscarCredencial(new UsuarioAdministrador("admin")).orElseThrow();
        jdbc.update("UPDATE administradores SET version_credenciales=version_credenciales+1");
        assertThrows(CredencialesAdministrativasInvalidasException.class,
            () -> repositorio.crear(TokenAdministrativo.crear(), esperada, AHORA, politica));
        assertEquals(0, cantidad("sesiones_administrativas"));
    }

    private long cantidad(String tabla) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Long.class); }
    private String token() throws Exception { return login("admin", CLAVE, 201).get("token").stringValue(); }
    private JsonNode login(String usuario, String clave, int estado) throws Exception {
        return comprobar(solicitar("POST", LOGIN, json.writeValueAsString(Map.of("usuario", usuario, "clave", clave)), null, "application/json"), estado);
    }
    private JsonNode get(String ruta, String token, int estado) throws Exception {
        return comprobar(solicitar("GET", ruta, null, token, null), estado);
    }
    private String url(String ruta) { return "http://127.0.0.1:" + puerto + ruta; }
    private HttpResponse<String> solicitar(String metodo, String ruta, String cuerpo, String token, String tipo) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(url(ruta))).timeout(Duration.ofSeconds(10));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (tipo != null) builder.header("Content-Type", tipo);
        builder.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode comprobar(HttpResponse<String> respuesta, int estado) throws Exception {
        assertEquals(estado, respuesta.statusCode(), respuesta.body());
        assertEquals("no-store", respuesta.headers().firstValue("Cache-Control").orElse(""));
        assertTrue(respuesta.headers().firstValue("Set-Cookie").isEmpty());
        if (estado == 401) assertEquals("Bearer", respuesta.headers().firstValue("WWW-Authenticate").orElse(""));
        assertTrue(respuesta.headers().firstValue("Content-Type").orElse("").startsWith(estado < 400 ? "application/json" : "application/problem+json"));
        var cuerpo = json.readTree(respuesta.body());
        if (estado >= 400) assertEquals(estado, cuerpo.get("status").intValue());
        return cuerpo;
    }
}
