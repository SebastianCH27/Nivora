package pe.nivora.backend.acceso.administradores;

import java.net.URI;
import java.net.http.*;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
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
import pe.nivora.backend.acceso.dominio.administradores.*;
import pe.nivora.backend.acceso.dominio.contratos.*;
import pe.nivora.backend.acceso.infraestructura.administradores.ProtectorClavesArgon2;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GestionAdministradoresApiTests {
    private static final String BASE = "/api/v1/administracion/administradores";
    private static final String LOGIN = "/api/v1/acceso/administradores/sesiones";
    private static final String SESION = "/api/v1/acceso/administradores/sesion";
    private static final String SUP = "/api/v1/supervision/suministros/13100390/lecturas";
    private static final String CLAVE = "Frase ficticia de administracion";
    private static final String TEMPORAL = "Clave temporal de otra persona";
    private static final String NUEVA = "Mi propia frase administrativa";
    private static final Instant AHORA = Instant.parse("2026-11-11T01:00:00.123456Z");
    private static final List<String> SCRIPTS = List.of("02-suministros.sql", "03-lecturas.sql",
        "04-recuperacion-lecturas.sql", "05-contexto-recuperacion-lecturas.sql",
        "06-sincronizacion-lecturas.sql", "07-administradores.sql", "08-sesiones-administrativas.sql");
    @Autowired private DataSource fuente;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JsonMapper json;
    @Autowired private InicializarAdministracion inicializar;
    @Autowired private RepositorioSesionesAdministrativas sesiones;
    @Autowired private RepositorioGestionAdministradores gestion;
    @Autowired private RelojMutable reloj;
    @Value("${local.server.port}") private int puerto;
    private Connection esquema;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> "jdbc:h2:mem:nivora_gestion_api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS nivora\\;SET SCHEMA nivora");
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
    }
    @BeforeEach
    void preparar() throws Exception {
        reloj.valor.set(AHORA);
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
    @AfterAll void cerrar() throws Exception { if (esquema != null) esquema.close(); http.close(); }

    @Test
    void laGestionExigeSesionYPermisoEnTodasLasRutas() throws Exception {
        for (String ruta : List.of(BASE, BASE + "/admin", BASE + "/admin/auditoria")) get(ruta, null, 401);
        String propietario = token();
        crear(propietario, "operador", false);
        completarClave("operador");
        String operador = ingresar("operador", NUEVA, 201);
        get(SUP, operador, 200);
        for (String ruta : List.of(BASE, BASE + "/admin", BASE + "/admin/auditoria")) get(ruta, operador, 403);
        enviar("POST", BASE, alta("intruso", NUEVA), operador, 403);
        for (String ruta : List.of("estado", "permisos", "clave")) enviar("PUT", BASE + "/admin/" + ruta, Map.of(), operador, 403);
        assertEquals(2, cantidad("administradores"));
    }

    @Test
    void altaPredeterminadaEsActivaSinGestionConCambioInicialYPaginaSegura() throws Exception {
        String token = token();
        var peticion = solicitar("POST", BASE, json.writeValueAsString(alta("OpErAdOr", TEMPORAL)), token, "application/json");
        var respuesta = comprobar(peticion, 201);
        assertEquals(BASE + "/operador", peticion.headers().firstValue("Location").orElse(""));
        var cuenta = respuesta.get("cuenta");
        assertEquals("operador", cuenta.get("usuario").stringValue());
        assertTrue(cuenta.get("activa").booleanValue());
        assertFalse(cuenta.get("gestionarAdministradores").booleanValue());
        assertTrue(cuenta.get("cambioClaveRequerido").booleanValue());
        assertEquals("1", cuenta.get("versionCredenciales").stringValue());
        assertFalse(respuesta.get("sesionActualRevocada").booleanValue());
        var pagina = get(BASE, token, 200);
        assertEquals(2, pagina.get("administradores").size());
        assertFalse(pagina.toString().contains("hash"));
        assertFalse(pagina.toString().contains(TEMPORAL));
        assertFalse(pagina.toString().contains(token));
        assertEquals(1, eventos("operador", "CREACION_ADMINISTRADOR"));
    }

    @Test
    void usuarioCanonicoDuplicadoNoSobrescribeClaveNiCuenta() throws Exception {
        String token = token(); crear(token, "operador", false);
        var antes = jdbc.queryForList("SELECT * FROM administradores");
        enviar("POST", BASE, alta("OPERADOR", NUEVA), token, 409);
        assertEquals(antes, jdbc.queryForList("SELECT * FROM administradores"));
        assertEquals(1, eventos("operador", "CREACION_ADMINISTRADOR"));
    }

    @Test
    void confirmarClaveActualEsObligatorioYNoAceptaLaClaveDelDestinatario() throws Exception {
        String token = token(); crear(token, "operador", false);
        var antes = jdbc.queryForList("SELECT * FROM administradores");
        enviar("POST", BASE, Map.of("usuario", "otro", "nombre", "Otro", "claveInicial", TEMPORAL, "claveActual", TEMPORAL), token, 401);
        enviar("PUT", BASE + "/operador/estado", Map.of("activa", false, "claveActual", TEMPORAL), token, 401);
        enviar("PUT", BASE + "/operador/permisos", Map.of("gestionarAdministradores", true, "claveActual", TEMPORAL), token, 401);
        enviar("PUT", BASE + "/operador/clave", Map.of("claveTemporal", NUEVA, "claveActual", TEMPORAL), token, 401);
        assertEquals(antes, jdbc.queryForList("SELECT * FROM administradores"));
    }

    @Test
    void nuevaCuentaSoloPuedeCambiarClaveConsultarSesionYCerrarHastaCompletarCambio() throws Exception {
        String propietario = token(); crear(propietario, "gestor", true);
        String temporal = ingresar("gestor", TEMPORAL, 201);
        assertEquals(0, get(SESION, temporal, 200).get("permisos").size());
        get(SUP, temporal, 403); get(BASE, temporal, 403);
        var cambio = cambiar(temporal, TEMPORAL, NUEVA, 200);
        assertTrue(cambio.get("sesionActualRevocada").booleanValue());
        assertFalse(cambio.get("cuenta").get("cambioClaveRequerido").booleanValue());
        get(SESION, temporal, 401);
        ingresar("gestor", TEMPORAL, 401);
        String definitiva = ingresar("gestor", NUEVA, 201);
        get(BASE, definitiva, 200); get(SUP, definitiva, 200);
        assertEquals(1, eventos("gestor", "CAMBIO_CLAVE"));
    }

    @Test
    void cambiarClavePropiaRevocaTodosLosDispositivosYConservaElPermiso() throws Exception {
        String uno = token(), dos = token();
        cambiar(uno, CLAVE, NUEVA, 200);
        get(SESION, uno, 401); get(SESION, dos, 401);
        ingresar("admin", CLAVE, 401);
        String nueva = ingresar("admin", NUEVA, 201);
        get(BASE, nueva, 200);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sesiones_administrativas WHERE revocada_en_utc IS NOT NULL", Integer.class));
    }

    @Test
    void claveNuevaCortaIgualOConfirmacionIncorrectaNoInvalidaSesion() throws Exception {
        String token = token();
        cambiar(token, CLAVE, "123456", 400);
        cambiar(token, CLAVE, CLAVE, 400);
        cambiar(token, TEMPORAL, NUEVA, 401);
        get(BASE, token, 200);
        assertEquals(1L, version("admin"));
    }

    @Test
    void concedeYRetiraPermisosRevocandoSesionesAnteriores() throws Exception {
        String propietario = token(); crear(propietario, "operador", false); completarClave("operador");
        String anterior = ingresar("operador", NUEVA, 201);
        permiso(propietario, "operador", true, 200);
        get(SESION, anterior, 401);
        String gestor = ingresar("operador", NUEVA, 201); get(BASE, gestor, 200);
        permiso(propietario, "operador", false, 200);
        get(SESION, gestor, 401);
        String operador = ingresar("operador", NUEVA, 201); get(BASE, operador, 403); get(SUP, operador, 200);
        assertEquals(1, eventos("operador", "CONCESION_GESTION_ADMINISTRADORES"));
        assertEquals(1, eventos("operador", "REVOCACION_GESTION_ADMINISTRADORES"));
    }

    @Test
    void desactivarRevocaYReactivarNoResucitaSesiones() throws Exception {
        String propietario = token(); crear(propietario, "operador", false); completarClave("operador");
        String uno = ingresar("operador", NUEVA, 201), dos = ingresar("operador", NUEVA, 201);
        estado(propietario, "operador", false, 200);
        get(SESION, uno, 401); get(SESION, dos, 401); ingresar("operador", NUEVA, 401);
        estado(propietario, "operador", true, 200);
        get(SESION, uno, 401); get(SUP, ingresar("operador", NUEVA, 201), 200);
        assertEquals(1, eventos("operador", "DESACTIVACION_ADMINISTRADOR"));
        assertEquals(1, eventos("operador", "ACTIVACION_ADMINISTRADOR"));
    }

    @Test
    void restablecerClaveTemporalExigeOtroCambioYRevocaSesiones() throws Exception {
        String propietario = token(); crear(propietario, "operador", false); completarClave("operador");
        String anterior = ingresar("operador", NUEVA, 201);
        enviar("PUT", BASE + "/operador/clave", Map.of("claveActual", CLAVE, "claveTemporal", CLAVE), propietario, 200);
        get(SESION, anterior, 401); ingresar("operador", NUEVA, 401);
        String temporal = ingresar("operador", CLAVE, 201); get(SUP, temporal, 403);
        cambiar(temporal, CLAVE, TEMPORAL, 200);
        get(SUP, ingresar("operador", TEMPORAL, 201), 200);
        assertEquals(1, eventos("operador", "RESTABLECIMIENTO_CLAVE"));
    }

    @Test
    void ultimaCuentaConGestionNoSeDesactivaNiPierdeElPermiso() throws Exception {
        String token = token();
        estado(token, "admin", false, 409);
        permiso(token, "admin", false, 409);
        enviar("PUT", BASE + "/admin/clave", Map.of("claveActual", CLAVE, "claveTemporal", TEMPORAL), token, 409);
        assertEquals(1L, version("admin")); get(BASE, token, 200);
    }

    @Test
    void segundoGestorConClavePendienteNoCuentaComoAccesoDeRespaldo() throws Exception {
        String token = token(); crear(token, "gestor", true);
        estado(token, "admin", false, 409); permiso(token, "admin", false, 409);
        completarClave("gestor");
        var cambio = permiso(token, "admin", false, 200);
        assertTrue(cambio.get("sesionActualRevocada").booleanValue());
        get(SESION, token, 401);
        get(BASE, ingresar("gestor", NUEVA, 201), 200);
    }

    @Test
    void cambioSinDiferenciasNoAuditaNoVersionaYNoRevoca() throws Exception {
        String token = token(); crear(token, "operador", false);
        String otra = ingresar("operador", TEMPORAL, 201);
        long eventos = cantidad("auditoria_administradores");
        assertFalse(estado(token, "operador", true, 200).get("modificada").booleanValue());
        assertFalse(permiso(token, "operador", false, 200).get("modificada").booleanValue());
        assertEquals(1L, version("operador"));
        assertEquals(eventos, cantidad("auditoria_administradores"));
        get(SESION, otra, 200);
    }

    @Test
    void listadoYAuditoriaSePaginanSinRepetirYConIdentificadoresTexto() throws Exception {
        String token = token(); crear(token, "alfa", false); crear(token, "zeta", false);
        var primera = get(BASE + "?limite=2", token, 200);
        assertTrue(primera.get("hayMas").booleanValue());
        assertEquals("alfa", primera.get("siguienteDespuesDe").stringValue());
        var segunda = get(BASE + "?limite=2&despuesDe=alfa", token, 200);
        assertEquals(1, segunda.get("administradores").size());
        assertEquals("zeta", segunda.get("administradores").get(0).get("usuario").stringValue());
        assertFalse(segunda.get("hayMas").booleanValue());
        estado(token, "alfa", false, 200);
        var auditoria = get(BASE + "/alfa/auditoria?limite=1", token, 200);
        assertTrue(auditoria.get("hayMas").booleanValue());
        var evento = auditoria.get("eventos").get(0);
        assertEquals("admin", evento.get("actorUsuario").stringValue());
        assertEquals("CREACION_ADMINISTRADOR", evento.get("operacion").stringValue());
        String cursor = auditoria.get("siguienteDespuesDe").stringValue();
        var resto = get(BASE + "/alfa/auditoria?despuesDe=" + cursor, token, 200);
        assertEquals("DESACTIVACION_ADMINISTRADOR", resto.get("eventos").get(0).get("operacion").stringValue());
        assertFalse(resto.toString().contains("clave"));
    }

    @Test
    void cuentaAusenteDevuelve404YUsuarioInvalido400() throws Exception {
        String token = token();
        get(BASE + "/ausente", token, 404); get(BASE + "/ausente/auditoria", token, 404);
        estado(token, "ausente", false, 404); permiso(token, "ausente", true, 404);
        enviar("PUT", BASE + "/ausente/clave", Map.of("claveActual", CLAVE, "claveTemporal", TEMPORAL), token, 404);
        get(BASE + "/12", token, 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?limite=0", "?limite=101", "?limite=-1", "?despuesDe=12", "?despuesDe=%20admin"})
    void cursoresYLimitesDeListadoInvalidos(String consulta) throws Exception { get(BASE + consulta, token(), 400); }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "01", "abc", "9223372036854775808", "99999999999999999999"})
    void cursorDeAuditoriaInvalido(String cursor) throws Exception { get(BASE + "/admin/auditoria?despuesDe=" + cursor, token(), 400); }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]", "null", "no-json",
        "{\"usuario\":\"nuevo\",\"nombre\":\"Nombre\",\"claveInicial\":\"Frase temporal de prueba\"}",
        "{\"usuario\":\"nuevo\",\"nombre\":\"Nombre\",\"claveInicial\":\"Frase temporal de prueba\",\"claveActual\":\"x\",\"rol\":\"ADMIN\"}",
        "{\"usuario\":\"nuevo\",\"nombre\":\"Nombre\",\"claveInicial\":\"Frase temporal de prueba\",\"claveActual\":\"x\",\"actor\":\"admin\"}",
        "{\"usuario\":\"nuevo\",\"usuario\":\"otro\",\"nombre\":\"Nombre\",\"claveInicial\":\"x\",\"claveActual\":\"x\"}",
        "{\"usuario\":\"nuevo\",\"nombre\":\"Nombre\",\"claveInicial\":\"x\",\"claveActual\":\"x\"} {}"})
    void altaRechazaCamposAusentesAdicionalesDuplicadosYJsonInvalido(String cuerpo) throws Exception {
        comprobar(solicitar("POST", BASE, cuerpo, token(), "application/json"), 400);
        assertEquals(1, cantidad("administradores"));
    }

    @Test
    void tiposBooleanosYTextoSonEstrictosYElCuerpoEstaAcotado() throws Exception {
        String token = token();
        comprobar(solicitar("POST", BASE, "x".repeat(4097), token, "application/json"), 413);
        comprobar(solicitar("POST", BASE, "{}", token, "text/plain"), 415);
        comprobar(solicitar("POST", BASE, "{}", token, "application/json;charset=UTF-16"), 415);
        enviar("PUT", BASE + "/admin/estado", Map.of("activa", "false", "claveActual", CLAVE), token, 400);
        enviar("PUT", BASE + "/admin/permisos", Map.of("gestionarAdministradores", 1, "claveActual", CLAVE), token, 400);
        var alta = new HashMap<>(alta("nuevo", TEMPORAL)); alta.put("gestionarAdministradores", "true");
        enviar("POST", BASE, alta, token, 400);
        alta.remove("gestionarAdministradores"); alta.put("nombre", 1); enviar("POST", BASE, alta, token, 400);
    }

    @Test
    void clavesNuevasNormalesAunqueLaClaveInicialExistenteSeaCorta() throws Exception {
        jdbc.update("UPDATE administradores SET hash_clave=?", new ProtectorClavesArgon2(true).proteger("314159".toCharArray()));
        String token = ingresar("admin", "314159", 201);
        enviar("POST", BASE, Map.of("usuario", "nuevo", "nombre", "Nombre", "claveInicial", "123456", "claveActual", "314159"), token, 400);
        cambiar(token, "314159", NUEVA, 200);
        get(BASE, ingresar("admin", NUEVA, 201), 200);
    }

    @Test
    void confirmacionTieneLimitePropioSinAgotarLosIniciosDeSesion() throws Exception {
        String token = token();
        for (int i = 0; i < 10; i++) enviar("PUT", BASE + "/admin/estado", Map.of("activa", true, "claveActual", TEMPORAL), token, 401);
        var limitada = solicitar("PUT", BASE + "/admin/estado", json.writeValueAsString(Map.of("activa", true, "claveActual", CLAVE)), token, "application/json");
        comprobar(limitada, 429);
        assertEquals("60", limitada.headers().firstValue("Retry-After").orElse(""));
        ingresar("admin", CLAVE, 201);
        reloj.valor.set(AHORA.plusSeconds(60));
        estado(token, "admin", true, 200);
        assertEquals(1L, version("admin"));
    }

    @Test
    void falloDeAuditoriaRevierteEstadoVersionYSesionesEnUnaTransaccion() throws Exception {
        String token = token(); crear(token, "operador", false);
        String otra = ingresar("operador", TEMPORAL, 201);
        jdbc.execute("DROP TABLE auditoria_administradores");
        var error = estado(token, "operador", false, 503);
        assertFalse(error.toString().contains("UPDATE"));
        assertTrue(jdbc.queryForObject("SELECT activa FROM administradores WHERE usuario='operador'", Boolean.class));
        assertEquals(1L, version("operador"));
        aplicar(); get(SESION, otra, 200);
    }

    @Test
    void falloDeAuditoriaRevierteTambienElAltaYElCambioDeClave() throws Exception {
        String token = token(); var antes = jdbc.queryForMap("SELECT * FROM administradores WHERE usuario='admin'");
        jdbc.execute("DROP TABLE auditoria_administradores");
        enviar("POST", BASE, alta("nuevo", TEMPORAL), token, 503);
        cambiar(token, CLAVE, NUEVA, 503);
        assertEquals(1, cantidad("administradores"));
        assertEquals(antes, jdbc.queryForMap("SELECT * FROM administradores WHERE usuario='admin'"));
        aplicar(); get(BASE, token, 200);
    }

    @Test
    void permisosYVersionSeRevalidanDentroDeLaTransaccion() throws Exception {
        var confirmacion = confirmacion(token());
        jdbc.update("UPDATE administradores SET gestionar_administradores=FALSE");
        assertThrows(PermisoGestionAdministrativaException.class, () -> gestion.cambiarEstado(confirmacion, new UsuarioAdministrador("admin"), true));
        jdbc.update("UPDATE administradores SET gestionar_administradores=TRUE, version_credenciales=version_credenciales+1");
        assertThrows(CredencialesAdministrativasInvalidasException.class, () -> gestion.cambiarEstado(confirmacion, new UsuarioAdministrador("admin"), true));
    }

    @Test
    void cierreCaducidadYCredencialCambiadaDuranteLaConfirmacionImpidenLaMutacion() throws Exception {
        var confirmacion = confirmacion(token());
        var esperado = confirmacion.credencial();
        jdbc.update("UPDATE administradores SET hash_clave=?", new ProtectorClavesArgon2().proteger(NUEVA.toCharArray()));
        assertThrows(CredencialesAdministrativasInvalidasException.class, () -> gestion.cambiarEstado(confirmacion, new UsuarioAdministrador("admin"), true));
        jdbc.update("UPDATE administradores SET hash_clave=?", esperado.hashClave());
        reloj.valor.set(AHORA.plusSeconds(900));
        assertThrows(CredencialesAdministrativasInvalidasException.class, () -> gestion.cambiarEstado(confirmacion, new UsuarioAdministrador("admin"), true));
        reloj.valor.set(AHORA);
        sesiones.cerrar(confirmacion.actor().token(), AHORA);
        assertThrows(CredencialesAdministrativasInvalidasException.class, () -> gestion.cambiarEstado(confirmacion, new UsuarioAdministrador("admin"), true));
    }

    @Test
    void noSePuedeUsarTokenDeOtroUsuarioNiCrearSinPermisoPorElPuertoInterno() throws Exception {
        String token = token(); crear(token, "operador", false);
        String otro = ingresar("operador", TEMPORAL, 201);
        var credencial = sesiones.buscarCredencial(new UsuarioAdministrador("admin")).orElseThrow();
        var falsa = new ConfirmacionAdministrativa(new ActorAdministrativo(TokenAdministrativo.interpretar(otro), new UsuarioAdministrador("admin")), credencial);
        assertThrows(CredencialesAdministrativasInvalidasException.class, () -> gestion.cambiarEstado(falsa, new UsuarioAdministrador("admin"), true));
        assertThrows(PermisoGestionAdministrativaException.class, () -> gestion.listar(confirmacion(otro).actor(), "", 20));
    }

    @Test
    void dosGestoresConcurrentesNoPuedenDejarElSistemaSinGestion() throws Exception {
        String token = token(); crear(token, "gestor", true); completarClave("gestor");
        var uno = confirmacion(token);
        var dos = confirmacion(ingresar("gestor", NUEVA, 201));
        var salida = new CountDownLatch(1);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var primero = hilos.submit(() -> retirarPropio(uno, salida));
            var segundo = hilos.submit(() -> retirarPropio(dos, salida));
            salida.countDown();
            assertEquals(1, primero.get(15, TimeUnit.SECONDS) + segundo.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM administradores WHERE activa=TRUE AND gestionar_administradores=TRUE AND cambio_clave_requerido=FALSE", Integer.class));
    }
    private int retirarPropio(ConfirmacionAdministrativa c, CountDownLatch salida) throws Exception {
        salida.await();
        try { gestion.cambiarPermiso(c, c.actor().usuario(), false); return 1; }
        catch (ConflictoGestionAdministrativaException esperado) { return 0; }
    }

    private ConfirmacionAdministrativa confirmacion(String token) {
        var sesion = jdbc.queryForObject("SELECT usuario FROM sesiones_administrativas WHERE hash_token=?", String.class, TokenAdministrativo.interpretar(token).huella());
        var usuario = new UsuarioAdministrador(sesion);
        return new ConfirmacionAdministrativa(new ActorAdministrativo(TokenAdministrativo.interpretar(token), usuario), sesiones.buscarCredencial(usuario).orElseThrow());
    }
    private long version(String usuario) { return jdbc.queryForObject("SELECT version_credenciales FROM administradores WHERE usuario=?", Long.class, usuario); }
    private long cantidad(String tabla) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Long.class); }
    private int eventos(String usuario, String operacion) { return jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_administradores WHERE afectado_usuario=? AND operacion=?", Integer.class, usuario, operacion); }
    private Map<String, Object> alta(String usuario, String clave) { return Map.of("usuario", usuario, "nombre", "Persona de prueba", "claveInicial", clave, "claveActual", CLAVE); }
    private JsonNode crear(String token, String usuario, boolean gestionar) throws Exception {
        var cuerpo = new HashMap<>(alta(usuario, TEMPORAL)); cuerpo.put("gestionarAdministradores", gestionar);
        return enviar("POST", BASE, cuerpo, token, 201);
    }
    private JsonNode permiso(String token, String usuario, boolean valor, int estado) throws Exception {
        return enviar("PUT", BASE + "/" + usuario + "/permisos", Map.of("gestionarAdministradores", valor, "claveActual", CLAVE), token, estado);
    }
    private JsonNode estado(String token, String usuario, boolean valor, int estado) throws Exception {
        return enviar("PUT", BASE + "/" + usuario + "/estado", Map.of("activa", valor, "claveActual", CLAVE), token, estado);
    }
    private void completarClave(String usuario) throws Exception { cambiar(ingresar(usuario, TEMPORAL, 201), TEMPORAL, NUEVA, 200); }
    private JsonNode cambiar(String token, String actual, String nueva, int estado) throws Exception {
        return enviar("PUT", SESION + "/clave", Map.of("claveActual", actual, "claveNueva", nueva), token, estado);
    }
    private String token() throws Exception { return ingresar("admin", CLAVE, 201); }
    private String ingresar(String usuario, String clave, int estado) throws Exception {
        var r = enviar("POST", LOGIN, Map.of("usuario", usuario, "clave", clave), null, estado);
        return estado == 201 ? r.get("token").stringValue() : null;
    }
    private JsonNode enviar(String metodo, String ruta, Map<String, ?> cuerpo, String token, int estado) throws Exception {
        return comprobar(solicitar(metodo, ruta, json.writeValueAsString(cuerpo), token, "application/json"), estado);
    }
    private JsonNode get(String ruta, String token, int estado) throws Exception { return comprobar(solicitar("GET", ruta, null, token, null), estado); }
    private HttpResponse<String> solicitar(String metodo, String ruta, String cuerpo, String token, String tipo) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta)).timeout(Duration.ofSeconds(10));
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (tipo != null) b.header("Content-Type", tipo);
        b.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode comprobar(HttpResponse<String> respuesta, int estado) throws Exception {
        assertEquals(estado, respuesta.statusCode(), respuesta.body());
        assertEquals("no-store", respuesta.headers().firstValue("Cache-Control").orElse(""));
        assertTrue(respuesta.headers().firstValue("Set-Cookie").isEmpty());
        if (estado == 401) assertEquals("Bearer", respuesta.headers().firstValue("WWW-Authenticate").orElse(""));
        var cuerpo = json.readTree(respuesta.body());
        if (estado >= 400) assertEquals(estado, cuerpo.get("status").intValue());
        assertFalse(respuesta.body().contains("$argon2"));
        return cuerpo;
    }
}
