package pe.nivora.backend.acceso.sesiones;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pe.nivora.backend.acceso.aplicacion.InicializarAdministracion;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.Dni;
import pe.nivora.backend.acceso.dominio.IdentidadConsultada;
import pe.nivora.backend.acceso.dominio.Suministro;
import pe.nivora.backend.acceso.dominio.consulta.TokenConsulta;
import pe.nivora.backend.acceso.dominio.contratos.FuenteIdentidad;
import pe.nivora.backend.acceso.dominio.contratos.FuenteIdentidadNoDisponibleException;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministros;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministrosNoDisponibleException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SesionesConsultaApiTests {

    private static final String LOGIN = "/api/v1/acceso/consulta/sesiones";
    private static final String SESION = "/api/v1/acceso/consulta/sesion";
    private static final String ADMIN = "/api/v1/acceso/administradores";
    private static final String SUPERVISION = "/api/v1/supervision/suministros/000101/lecturas";
    private static final String CODIGO = "000101";
    private static final String DNI = "00000001";
    private static final String NOMBRE = "María Persona Ejemplo";
    private static final String SOLICITUD = "{\"codigoSuministro\":\"000101\",\"dni\":\"00000001\"}";
    private static final Instant AHORA = Instant.parse("2026-11-11T13:00:00.123456Z");

    @Autowired private DataSource datos;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JsonMapper json;
    @Autowired private FuentesPrueba fuentes;
    @Autowired private RelojPrueba reloj;
    @Autowired private InicializarAdministracion inicializar;
    @Value("${local.server.port}") private int puerto;

    private Connection esquema;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () ->
            "jdbc:h2:mem:nivora_consulta_api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS nivora\\;SET SCHEMA nivora");
        registro.add("nivora.sesiones-consulta.intentos-por-origen", () -> 3);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionPrueba {
        @Bean @Primary FuentesPrueba fuentesPrueba() {
            return new FuentesPrueba();
        }

        @Bean @Primary RelojPrueba relojPrueba() {
            return new RelojPrueba();
        }
    }

    @BeforeEach
    void preparar() throws Exception {
        if (esquema == null) {
            esquema = datos.getConnection();
        }
        new ResourceDatabasePopulator(new FileSystemResource("base-datos/02-suministros.sql"),
            new FileSystemResource("base-datos/07-administradores.sql"),
            new FileSystemResource("base-datos/08-sesiones-administrativas.sql"),
            new FileSystemResource("base-datos/09-sesiones-consulta.sql")).populate(esquema);
        for (String tabla : List.of("sesiones_consulta", "limites_acceso_consulta",
                "sesiones_administrativas", "limites_acceso_administrativo",
                "auditoria_administradores", "administradores", "suministros")) {
            jdbc.execute("DELETE FROM " + tabla);
        }
        fuentes.reiniciar();
        reloj.valor.set(AHORA);
    }

    @AfterAll
    void cerrar() throws Exception {
        if (esquema != null) {
            esquema.close();
        }
        http.close();
    }

    @Test
    void iniciaConUnaPersonaDistintaAlTitularYRegistraElAccesoCompleto() throws Exception {
        var respuesta = enviar(peticion("POST", LOGIN, SOLICITUD, null), 201);
        var inicio = json.readTree(respuesta.body());
        var sesion = inicio.path("sesion");
        assertEquals("Bearer", inicio.path("tipoToken").stringValue());
        String token = inicio.path("token").stringValue();
        assertTrue(token.matches("nc_[A-Za-z0-9_-]{43}"));
        assertEquals("CONSULTA", sesion.path("rol").stringValue());
        assertEquals(CODIGO, sesion.path("codigoSuministro").stringValue());
        assertEquals(NOMBRE, sesion.path("nombre").stringValue());
        assertEquals(AHORA.toString(), sesion.path("creadaEn").stringValue());
        assertEquals(AHORA.plusSeconds(1800).toString(), sesion.path("expiraEn").stringValue());
        assertFalse(respuesta.body().contains(DNI));
        assertFalse(respuesta.body().contains("Titular ficticio"));
        assertTrue(respuesta.headers().allValues("Set-Cookie").isEmpty());

        Map<String, Object> acceso = jdbc.queryForMap("SELECT * FROM sesiones_consulta");
        assertEquals(DNI, acceso.get("dni_utilizado"));
        assertEquals(NOMBRE, acceso.get("nombre_consultado"));
        assertEquals(CODIGO, acceso.get("codigo_suministro"));
        assertEquals(sesion.path("idSesion").stringValue(), acceso.get("id_sesion"));
        assertEquals(new TokenConsulta(token).huella(), acceso.get("hash_token"));
        assertFalse(acceso.containsValue(token));
        assertEquals(1, cantidad("suministros"));
    }

    @Test
    void cierraSoloLaSesionActualYConservaElRegistroDelAcceso() throws Exception {
        String uno = token(SOLICITUD);
        String dos = token(SOLICITUD.replace(CODIGO, "000102"));
        assertNotEquals(uno, dos);
        fuentes.modo = Modo.AGUA_CAIDA;
        var actual = json.readTree(enviar(peticion("GET", SESION + "?codigoSuministro=000102",
            null, uno), 200).body());
        assertEquals(CODIGO, actual.path("codigoSuministro").stringValue());
        assertFalse(actual.has("token"));
        enviar(peticion("DELETE", SESION, null, uno), 204);
        enviar(peticion("GET", SESION, null, uno), 401);
        enviar(peticion("GET", SESION, null, dos), 200);
        assertEquals(2, cantidad("sesiones_consulta"));
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM sesiones_consulta WHERE revocada_en_utc IS NOT NULL", Integer.class));
        assertEquals(2, fuentes.suministros.get());
        assertEquals(2, fuentes.identidades.get());
    }

    @Test
    void caducaEnLaFechaFijadaAunqueHayaActividad() throws Exception {
        String token = token(SOLICITUD);
        reloj.valor.set(AHORA.plusSeconds(1799));
        enviar(peticion("GET", SESION, null, token), 200);
        reloj.valor.set(AHORA.plusSeconds(1800));
        enviar(peticion("GET", SESION, null, token), 401);
        assertEquals(1, cantidad("sesiones_consulta"));
    }

    @Test
    void losTokensDeConsultaYAdministracionTienenRutasSeparadas() throws Exception {
        String consulta = token(SOLICITUD);
        String clave = "Frase ficticia de administracion";
        inicializar.ejecutar("admin", "Admin de prueba", clave.toCharArray());
        var inicioAdmin = enviar(peticion("POST", ADMIN + "/sesiones",
            json.writeValueAsString(Map.of("usuario", "admin", "clave", clave)), null), 201);
        String admin = json.readTree(inicioAdmin.body()).path("token").stringValue();

        enviar(peticion("GET", SESION, null, admin), 401);
        enviar(peticion("GET", ADMIN + "/sesion", null, consulta), 401);
        enviar(peticion("GET", SUPERVISION, null, consulta), 401);
        enviar(peticion("GET", ADMIN + "/sesion", null, admin), 200);
        enviar(peticion("GET", SESION, null, consulta), 200);
    }

    @Test
    void laSesionExigeLaCabeceraBearer() throws Exception {
        String token = token(SOLICITUD);
        var respuesta = enviar(peticion("GET", SESION + "?token=" + token, null, null), 401);
        assertEquals("Bearer", respuesta.headers().firstValue("WWW-Authenticate").orElseThrow());
        assertFalse(respuesta.body().contains(token));
        enviar(HttpRequest.newBuilder(URI.create(url(SESION)))
            .header("Cookie", "sesion=" + token).GET().build(), 401);
        enviar(HttpRequest.newBuilder(URI.create(url(SESION)))
            .header("Authorization", "Basic " + token).GET().build(), 401);
        enviar(HttpRequest.newBuilder(URI.create(url(SESION)))
            .header("Authorization", "Bearer " + token).header("Authorization", "Bearer " + token)
            .GET().build(), 401);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"codigoSuministro\":\"000101\",\"dni\":1}",
        "{\"codigoSuministro\":101,\"dni\":\"00000001\"}",
        "{\"codigoSuministro\":\"abc\",\"dni\":\"00000001\"}",
        "{\"codigoSuministro\":\"000101\",\"dni\":\"0000001\"}",
        "{\"codigoSuministro\":\"000101\",\"dni\":\"00000001\",\"rol\":\"ADMIN\"}",
        "{\"codigoSuministro\":\"000101\",\"dni\":\"00000001\",\"dni\":\"00000002\"}",
        "{\"codigoSuministro\":\"000101\",\"dni\":\"00000001\"} {}"
    })
    void descartaEntradasInvalidasAntesDeContactarFuentes(String cuerpo) throws Exception {
        enviar(peticion("POST", LOGIN, cuerpo, null), 400);
        assertEquals(0, fuentes.suministros.get());
        assertEquals(0, fuentes.identidades.get());
        assertEquals(0, cantidad("limites_acceso_consulta"));
        assertEquals(0, cantidad("sesiones_consulta"));
    }

    @Test
    void descartaSolicitudesGrandesOConTipoIncorrecto() throws Exception {
        enviar(peticion("POST", LOGIN, " ".repeat(4097), null), 413);
        enviar(HttpRequest.newBuilder(URI.create(url(LOGIN))).header("Content-Type", "text/plain")
            .POST(HttpRequest.BodyPublishers.ofString(SOLICITUD)).build(), 415);
        assertEquals(0, fuentes.suministros.get());
    }

    @Test
    void unSuministroAusenteEvitaConsultarElDni() throws Exception {
        fuentes.modo = Modo.SUMINISTRO_AUSENTE;
        enviar(peticion("POST", LOGIN, SOLICITUD, null), 401);
        assertEquals(1, fuentes.suministros.get());
        assertEquals(0, fuentes.identidades.get());
        assertEquals(0, cantidad("sesiones_consulta"));
    }

    @Test
    void unaCaidaDeLaEmpresaConDatosEnCacheImpideUnAccesoNuevo() throws Exception {
        jdbc.update("INSERT INTO suministros VALUES (?, 'Titular en cache', '2026-11-11 12:00:00')", CODIGO);
        fuentes.modo = Modo.AGUA_CAIDA;
        enviar(peticion("POST", LOGIN, SOLICITUD, null), 503);
        assertEquals(0, fuentes.identidades.get());
        assertEquals(0, cantidad("sesiones_consulta"));
        assertEquals(1, cantidad("suministros"));
    }

    @Test
    void unDniSinConfirmarNoCreaSesionNiGuardaElSuministroNuevo() throws Exception {
        fuentes.modo = Modo.IDENTIDAD_CAIDA;
        var respuesta = enviar(peticion("POST", LOGIN, SOLICITUD, null), 503);
        assertFalse(respuesta.body().contains(DNI));
        assertEquals(1, fuentes.identidades.get());
        assertEquals(0, cantidad("sesiones_consulta"));
        assertEquals(0, cantidad("suministros"));
    }

    @Test
    void unaIdentidadDeOtroDocumentoSeRechaza() throws Exception {
        fuentes.modo = Modo.DNI_DISTINTO;
        enviar(peticion("POST", LOGIN, SOLICITUD, null), 503);
        assertEquals(0, cantidad("sesiones_consulta"));
    }

    @Test
    void unFalloAlInsertarSesionRevierteElRegistroDelSuministro() throws Exception {
        jdbc.execute("DROP TABLE sesiones_consulta");
        enviar(peticion("POST", LOGIN, SOLICITUD, null), 503);
        assertEquals(0, cantidad("suministros"));
    }

    @Test
    void elLimiteSeAplicaAntesDeConsultarYSeReabreAlVencerLaVentana() throws Exception {
        for (int intento = 0; intento < 3; intento++) {
            token(SOLICITUD);
        }
        var bloqueada = enviar(HttpRequest.newBuilder(URI.create(url(LOGIN)))
            .header("X-Forwarded-For", "192.0.2.1").header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(SOLICITUD)).build(), 429);
        assertEquals("60", bloqueada.headers().firstValue("Retry-After").orElseThrow());
        assertEquals(3, fuentes.identidades.get());
        reloj.valor.set(AHORA.plusSeconds(60));
        token(SOLICITUD);
        assertEquals(4, fuentes.identidades.get());
    }

    @Test
    void dosIntentosConcurrentesSoloPuedenConsumirElCupoRestanteUnaVez() throws Exception {
        token(SOLICITUD);
        token(SOLICITUD);
        var primera = http.sendAsync(peticion("POST", LOGIN, SOLICITUD, null),
            HttpResponse.BodyHandlers.ofString());
        var segunda = http.sendAsync(peticion("POST", LOGIN, SOLICITUD, null),
            HttpResponse.BodyHandlers.ofString());
        var estados = List.of(primera.get(15, TimeUnit.SECONDS).statusCode(),
            segunda.get(15, TimeUnit.SECONDS).statusCode()).stream().sorted().toList();
        assertEquals(List.of(201, 429), estados);
        assertEquals(3, cantidad("sesiones_consulta"));
        assertEquals(3, fuentes.identidades.get());
    }

    private String token(String cuerpo) throws Exception {
        JsonNode inicio = json.readTree(enviar(peticion("POST", LOGIN, cuerpo, null), 201).body());
        return inicio.path("token").stringValue();
    }

    private HttpRequest peticion(String metodo, String ruta, String cuerpo, String token) {
        var solicitud = HttpRequest.newBuilder(URI.create(url(ruta))).timeout(Duration.ofSeconds(10));
        if (token != null) {
            solicitud.header("Authorization", "Bearer " + token);
        }
        if (cuerpo != null) {
            solicitud.header("Content-Type", "application/json");
        }
        return solicitud.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(cuerpo)).build();
    }

    private HttpResponse<String> enviar(HttpRequest solicitud, int estado) throws Exception {
        var respuesta = http.send(solicitud, HttpResponse.BodyHandlers.ofString());
        assertEquals(estado, respuesta.statusCode(), respuesta.body());
        assertTrue(respuesta.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        return respuesta;
    }

    private String url(String ruta) {
        return "http://127.0.0.1:" + puerto + ruta;
    }

    private int cantidad(String tabla) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
    }

    enum Modo { OK, SUMINISTRO_AUSENTE, AGUA_CAIDA, IDENTIDAD_CAIDA, DNI_DISTINTO }

    static final class FuentesPrueba implements FuenteSuministros, FuenteIdentidad {
        final AtomicInteger suministros = new AtomicInteger();
        final AtomicInteger identidades = new AtomicInteger();
        volatile Modo modo = Modo.OK;

        void reiniciar() {
            modo = Modo.OK;
            suministros.set(0);
            identidades.set(0);
        }

        @Override
        public Optional<Suministro> consultar(CodigoSuministro codigo) {
            suministros.incrementAndGet();
            if (modo == Modo.AGUA_CAIDA) {
                throw new FuenteSuministrosNoDisponibleException("Fallo simulado");
            }
            return modo == Modo.SUMINISTRO_AUSENTE ? Optional.empty()
                : Optional.of(new Suministro(codigo, "Titular ficticio"));
        }

        @Override
        public IdentidadConsultada consultar(Dni dni) {
            identidades.incrementAndGet();
            if (modo == Modo.IDENTIDAD_CAIDA) {
                throw new FuenteIdentidadNoDisponibleException(
                    FuenteIdentidadNoDisponibleException.Motivo.RESULTADO_NO_CONCLUYENTE);
            }
            return new IdentidadConsultada(modo == Modo.DNI_DISTINTO ? new Dni("00000002") : dni, NOMBRE);
        }
    }

    static final class RelojPrueba extends Clock {
        final AtomicReference<Instant> valor = new AtomicReference<>(AHORA);

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zona) { return this; }
        @Override public Instant instant() { return valor.get(); }
    }
}
