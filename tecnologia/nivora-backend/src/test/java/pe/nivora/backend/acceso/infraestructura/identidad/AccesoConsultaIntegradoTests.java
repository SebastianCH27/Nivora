package pe.nivora.backend.acceso.infraestructura.identidad;

import java.io.IOException;
import java.math.BigDecimal;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import pe.nivora.backend.acceso.aplicacion.InicializarAdministracion;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.contratos.FuenteIdentidad;
import pe.nivora.backend.lecturas.dominio.LecturaAcumulada;
import pe.nivora.backend.lecturas.dominio.LoteLecturas;
import pe.nivora.backend.lecturas.dominio.ResultadoIncorporacion;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturas;
import pe.nivora.backend.recibos.aplicacion.IncorporarRecibos;
import pe.nivora.backend.recibos.aplicacion.SincronizarRecibos;
import pe.nivora.backend.recibos.dominio.ReciboEmitido;
import pe.nivora.backend.recibos.dominio.RecibosEmitidos;
import pe.nivora.backend.recibos.dominio.RepositorioRecibos;
import pe.nivora.backend.recibos.dominio.RepositorioSincronizacionRecibos;
import pe.nivora.backend.tarifas.aplicacion.IncorporarTarifas;
import pe.nivora.backend.tarifas.dominio.HistorialTarifario;
import pe.nivora.backend.tarifas.dominio.RepositorioTarifas;
import pe.nivora.backend.tarifas.dominio.VersionTarifaria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccesoConsultaIntegradoTests {

    private static final String CONSULTA = "/api/v1/acceso/consulta";
    private static final String CONSUMO = CONSULTA + "/consumo-actual";
    private static final String HISTORIAL = CONSULTA + "/historial";
    private static final String ESTADISTICAS = CONSULTA + "/estadisticas";
    private static final String COSTOS = CONSULTA + "/costos";
    private static final String PROYECCION = CONSULTA + "/proyeccion";
    private static final String RECIBOS = CONSULTA + "/recibos";
    private static final String ADMIN = "/api/v1/acceso/administradores";
    private static final String GESTION_TARIFAS = "/api/v1/administracion/suministros/000101/tarifas";
    private static final String SUPERVISION = "/api/v1/supervision/suministros/000101/lecturas";
    private static final String DNI = "00000001";
    private static final String TOKEN_PROVEEDOR = "token-ficticio-integracion";
    private static final String SOLICITUD = "{\"codigoSuministro\":\"000101\",\"dni\":\"00000001\"}";
    private static final String CLAVE_ADMIN = "Frase ficticia de administracion";
    private static final Instant AHORA = Instant.parse("2026-11-11T13:00:00Z");
    private static final AtomicReference<Instant> instantePrueba = new AtomicReference<>(AHORA);
    private static final List<String> SCRIPTS = List.of("02-suministros.sql", "03-lecturas.sql",
        "04-recuperacion-lecturas.sql", "05-contexto-recuperacion-lecturas.sql",
        "06-sincronizacion-lecturas.sql", "07-administradores.sql",
        "08-sesiones-administrativas.sql", "09-sesiones-consulta.sql", "10-tarifas.sql",
        "11-gestion-tarifas.sql", "12-recibos.sql", "13-sincronizacion-recibos.sql");

    private static final Respuesta AGUA_OK = new Respuesta(200, "application/json",
        "{\"codigoSuministro\":\"000101\",\"titular\":\"Titular ficticio\"}");
    private static final Respuesta IDENTIDAD_OK = new Respuesta(200, "application/json",
        "{\"dni\":\"00000001\",\"nombres\":\"Persona\",\"apellidoPaterno\":\"Ficticia\"}");
    private static final Respuesta CAIDA = new Respuesta(503, "application/json", "{}");
    private static final AtomicReference<Respuesta> agua = new AtomicReference<>(AGUA_OK);
    private static final AtomicReference<Respuesta> tarifasProveedor = new AtomicReference<>(CAIDA);
    private static final AtomicReference<Respuesta> recibosProveedor = new AtomicReference<>(CAIDA);
    private static final AtomicReference<Respuesta> identidad = new AtomicReference<>(IDENTIDAD_OK);
    private static final AtomicInteger llamadasAgua = new AtomicInteger();
    private static final AtomicInteger llamadasTarifas = new AtomicInteger();
    private static final AtomicInteger llamadasRecibos = new AtomicInteger();
    private static final AtomicInteger llamadasIdentidad = new AtomicInteger();
    private static final AtomicReference<String> peticionAgua = new AtomicReference<>();
    private static final AtomicReference<PeticionIdentidad> peticionIdentidad = new AtomicReference<>();
    private static final HttpServer fuentes = iniciarFuentes();

    @Autowired private DataSource datos;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JsonMapper json;
    @Autowired private InicializarAdministracion inicializar;
    @Autowired private RepositorioLecturas repositorioLecturas;
    @Autowired private RepositorioTarifas tarifas;
    @Autowired private IncorporarTarifas incorporarTarifas;
    @Autowired private RepositorioRecibos recibos;
    @Autowired private IncorporarRecibos incorporarRecibos;
    @Autowired private RepositorioSincronizacionRecibos programacionRecibos;
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
            return new RelojPrueba(ZoneOffset.UTC);
        }
    }

    @BeforeEach
    void preparar() throws Exception {
        instantePrueba.set(AHORA);
        if (esquema == null) {
            esquema = datos.getConnection();
        }
        var scripts = SCRIPTS.stream().map(s -> new FileSystemResource("base-datos/" + s))
            .toArray(FileSystemResource[]::new);
        new ResourceDatabasePopulator(scripts).populate(esquema);
        for (String tabla : List.of("sesiones_consulta", "limites_acceso_consulta",
                "sesiones_administrativas", "limites_acceso_administrativo",
                "tarifas_publicaciones", "auditoria_administradores", "administradores", "lecturas",
                "control_incorporacion_lecturas",
                "contexto_recuperacion_lecturas", "progreso_recuperacion_lecturas",
                "programacion_sincronizacion_lecturas", "tarifas_asignaciones", "tarifas_historiales",
                "tarifas_versiones", "programacion_sincronizacion_recibos", "recibos", "suministros")) {
            jdbc.execute("DELETE FROM " + tabla);
        }
        agua.set(AGUA_OK);
        tarifasProveedor.set(CAIDA);
        recibosProveedor.set(CAIDA);
        identidad.set(IDENTIDAD_OK);
        llamadasAgua.set(0);
        llamadasTarifas.set(0);
        llamadasRecibos.set(0);
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
        enviar("GET", ESTADISTICAS, null, admin, 401);
        enviar("GET", COSTOS, null, admin, 401);
        enviar("GET", PROYECCION, null, admin, 401);
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

    @Test
    void estadisticasComparanLosTramosYCalculanElResumenSoloConMesesCompletos() throws Exception {
        enviar("GET", ESTADISTICAS, null, null, 401);
        String sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        agua.set(CAIDA);
        identidad.set(CAIDA);
        jdbc.update("INSERT INTO suministros (codigo, titular, verificado_en_utc) VALUES (?, ?, ?)",
            "000102", "Otro titular ficticio", LocalDateTime.parse("2026-11-01T00:00:00"));
        lectura("empresa-agua", "1", "000101", "2026-06-02T05:00:00", 450);
        lectura("empresa-agua", "2", "000101", "2026-07-01T05:00:00", 500);
        lectura("empresa-agua", "3", "000101", "2026-08-01T05:00:00", 500);
        lectura("empresa-agua", "4", "000101", "2026-09-01T05:00:00", 600);
        lectura("empresa-agua", "5", "000101", "2026-09-15T05:00:00", 590);
        lectura("empresa-agua", "6", "000101", "2026-10-01T05:00:00", 700);
        lectura("empresa-agua", "7", "000101", "2026-11-01T05:00:00", 1000);
        lectura("empresa-agua", "8", "000101", "2026-11-11T12:59:59.999999", 1150);
        lectura("empresa-agua", "9", "000101", "2026-11-11T13:00:00.000001", 9000);
        lectura("empresa-agua", "10", "000102", "2026-11-10T05:00:00", 10000);
        lectura("otra-fuente", "11", "000101", "2026-10-12T05:00:00", 11000);

        var respuesta = enviar("GET", ESTADISTICAS + "?meses=6&codigoSuministro=000102", null, sesion, 200);
        var cuerpo = json.readTree(respuesta.body());
        assertEquals("000101", cuerpo.path("codigoSuministro").stringValue());
        var comparacion = cuerpo.path("comparacion");
        assertEquals("2026-11", comparacion.at("/actual/periodo").stringValue());
        assertEquals("150", comparacion.at("/actual/consumo/litros").stringValue());
        assertEquals("892799.999999", comparacion.at("/actual/duracionSegundos").stringValue());
        assertEquals("2026-10", comparacion.at("/anterior/periodo").stringValue());
        assertEquals("300", comparacion.at("/anterior/consumo/litros").stringValue());
        assertEquals("2678400", comparacion.at("/anterior/duracionSegundos").stringValue());
        assertEquals("-150", comparacion.at("/diferencia/litros").stringValue());
        assertEquals("-0.150", comparacion.at("/diferencia/metrosCubicos").stringValue());
        assertEquals(comparacion.at("/actual/consumo/idLecturaInicial"),
            comparacion.at("/anterior/consumo/idLecturaFinal"));

        var serie = cuerpo.path("serieMensual");
        assertEquals(6, serie.size());
        assertEquals("2026-05", serie.get(0).path("periodo").stringValue());
        assertEquals("SIN_DATOS", serie.get(0).at("/consumo/estado").stringValue());
        assertTrue(serie.get(0).at("/consumo/litros").isNull());
        assertEquals("PARCIAL", serie.get(1).at("/consumo/estado").stringValue());
        assertEquals("50", serie.get(1).at("/consumo/litros").stringValue());
        assertEquals("0", serie.get(2).at("/consumo/litros").stringValue());
        assertEquals("INCOMPATIBLE", serie.get(4).at("/consumo/estado").stringValue());
        assertEquals("2026-10", serie.get(5).path("periodo").stringValue());
        var resumen = cuerpo.path("resumen");
        assertEquals(3, resumen.path("mesesCompletos").intValue());
        assertEquals(1, resumen.path("mesesParciales").intValue());
        assertEquals(1, resumen.path("mesesSinDatos").intValue());
        assertEquals(1, resumen.path("mesesIncompatibles").intValue());
        assertEquals("400", resumen.at("/total/litros").stringValue());
        assertEquals("133.333", resumen.at("/promedio/litros").stringValue());
        assertEquals("0.133333", resumen.at("/promedio/metrosCubicos").stringValue());
        assertEquals("0", resumen.at("/minimo/litros").stringValue());
        assertEquals("300", resumen.at("/maximo/litros").stringValue());

        assertFalse(respuesta.body().contains(DNI));
        assertFalse(respuesta.body().contains(sesion));
        assertEquals(1, llamadasAgua.get());
        assertEquals(1, llamadasIdentidad.get());
        enviar("DELETE", CONSULTA + "/sesion", null, sesion, 204);
        enviar("GET", ESTADISTICAS, null, sesion, 401);
    }

    @Test
    void estadisticasSinMesesCompletosConservanLaSerieYDejanLosTotalesIndefinidos() throws Exception {
        String sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        lectura("empresa-agua", "1", "000101", "2026-10-01T05:00:00", 150);
        lectura("empresa-agua", "2", "000101", "2026-10-02T05:00:00", 175);

        var cuerpo = json.readTree(enviar("GET", ESTADISTICAS, null, sesion, 200).body());
        assertEquals(12, cuerpo.path("serieMensual").size());
        assertEquals("2025-11", cuerpo.at("/serieMensual/0/periodo").stringValue());
        assertEquals("2026-10", cuerpo.at("/serieMensual/11/periodo").stringValue());
        assertEquals("PARCIAL", cuerpo.at("/comparacion/anterior/consumo/estado").stringValue());
        assertEquals("25", cuerpo.at("/comparacion/anterior/consumo/litros").stringValue());
        assertEquals("86400", cuerpo.at("/comparacion/anterior/duracionSegundos").stringValue());
        assertTrue(cuerpo.at("/comparacion/actual/duracionSegundos").isNull());
        assertTrue(cuerpo.at("/comparacion/diferencia").isNull());
        assertEquals(0, cuerpo.at("/resumen/mesesCompletos").intValue());
        assertEquals(11, cuerpo.at("/resumen/mesesSinDatos").intValue());
        for (String campo : List.of("total", "promedio", "minimo", "maximo")) {
            assertTrue(cuerpo.at("/resumen/" + campo).isNull());
        }
        enviar("GET", ESTADISTICAS + "?meses=13", null, sesion, 400);
    }

    @Test
    void consumoHistorialYEstadisticasCoincidenAlCambiarElMesDeLima() throws Exception {
        instantePrueba.set(Instant.parse("2026-11-01T04:59:59.999999Z"));
        String sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        agua.set(CAIDA);
        identidad.set(CAIDA);
        incorporarLectura("1", "2026-10-01T05:00:00Z", 100);
        incorporarLectura("2", "2026-10-31T05:00:00Z", 250);

        var antes = consultarJson(CONSUMO, sesion);
        assertEquals("2026-10", antes.path("periodo").stringValue());
        assertEquals("150", antes.at("/consumo/litros").stringValue());
        var historialAntes = consultarJson(HISTORIAL + "?meses=1", sesion);
        assertEquals("2026-09", historialAntes.at("/periodos/0/periodo").stringValue());
        var comparacionAntes = consultarJson(ESTADISTICAS + "?meses=1", sesion).path("comparacion");
        assertEquals(antes.path("consumo"), comparacionAntes.at("/actual/consumo"));
        assertTrue(comparacionAntes.path("diferencia").isNull());

        instantePrueba.set(Instant.parse("2026-11-01T05:00:01Z"));
        var mesNuevo = consultarJson(CONSUMO, sesion);
        assertEquals("2026-11", mesNuevo.path("periodo").stringValue());
        assertEquals("SIN_DATOS", mesNuevo.at("/consumo/estado").stringValue());
        assertEquals("2026-10-31T05:00:00Z", mesNuevo.at("/actualizacion/ultimaLectura").stringValue());
        var octubreParcial = consultarJson(HISTORIAL + "?meses=1", sesion).at("/periodos/0");
        assertEquals("2026-10", octubreParcial.path("periodo").stringValue());
        assertEquals("PARCIAL", octubreParcial.at("/consumo/estado").stringValue());
        assertEquals("FALTA_LECTURA_FINAL", octubreParcial.at("/consumo/motivo").stringValue());

        incorporarLectura("3", "2026-11-01T05:00:00Z", 300);
        var octubre = consultarJson(HISTORIAL + "?meses=1", sesion).at("/periodos/0");
        assertEquals("COMPLETO", octubre.at("/consumo/estado").stringValue());
        assertEquals("200", octubre.at("/consumo/litros").stringValue());
        assertEquals("3", octubre.at("/consumo/idLecturaFinal").stringValue());

        instantePrueba.set(Instant.parse("2026-11-01T05:00:02Z"));
        incorporarLectura("4", "2026-11-01T05:00:01Z", 300);
        var noviembre = consultarJson(CONSUMO, sesion);
        assertEquals("0", noviembre.at("/consumo/litros").stringValue());
        assertEquals("3", noviembre.at("/consumo/idLecturaInicial").stringValue());
        assertEquals("2026-11-01T05:00:01Z", noviembre.at("/actualizacion/ultimaLectura").stringValue());

        var estadisticas = consultarJson(ESTADISTICAS + "?meses=2", sesion);
        assertEquals(noviembre.path("consumo"), estadisticas.at("/comparacion/actual/consumo"));
        assertEquals(octubre.path("consumo"), estadisticas.at("/comparacion/anterior/consumo"));
        assertEquals("1", estadisticas.at("/comparacion/actual/duracionSegundos").stringValue());
        assertEquals("2678400", estadisticas.at("/comparacion/anterior/duracionSegundos").stringValue());
        assertEquals("-200", estadisticas.at("/comparacion/diferencia/litros").stringValue());
        assertEquals("SIN_DATOS", estadisticas.at("/serieMensual/0/consumo/estado").stringValue());
        assertEquals(octubre, estadisticas.at("/serieMensual/1"));
        assertEquals(1, estadisticas.at("/resumen/mesesCompletos").intValue());
        assertEquals(1, estadisticas.at("/resumen/mesesSinDatos").intValue());
        assertEquals("200", estadisticas.at("/resumen/total/litros").stringValue());
        assertEquals(1, llamadasAgua.get());
        assertEquals(1, llamadasIdentidad.get());

        enviar("DELETE", CONSULTA + "/sesion", null, sesion, 204);
        for (String ruta : List.of(CONSUMO, HISTORIAL, ESTADISTICAS)) {
            enviar("GET", ruta, null, sesion, 401);
        }
    }

    @Test
    void costosUsanElConsumoYLaVersionAplicableDeCadaMes() throws Exception {
        enviar("GET", COSTOS, null, null, 401);
        String sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        agua.set(CAIDA);
        identidad.set(CAIDA);
        incorporarLectura("1", "2026-10-01T05:00:00Z", 100);
        incorporarLectura("2", "2026-11-01T05:00:00Z", 12100);
        incorporarLectura("3", "2026-11-10T05:00:00Z", 32100);

        var octubre = LocalDate.parse("2026-10-01");
        var noviembre = octubre.plusMonths(1);
        var historica = new VersionTarifaria("HUAMANGA-PRUEBA", 1, "Version anterior", octubre,
            new BigDecimal("3.60"), new BigDecimal("18"), List.of(
                tramoCosto("0", "8", "1.16", "0.51"),
                tramoCosto("8", "18", "1.28", "0.56"),
                tramoCosto("18", null, "1.99", "0.89")));
        var vigente = new VersionTarifaria("HUAMANGA-PRUEBA", 2, "Version siguiente", noviembre,
            new BigDecimal("4.60"), historica.igvPorcentaje(), historica.tramos());
        tarifas.guardar(new HistorialTarifario("empresa-agua", new CodigoSuministro("000101"), List.of(
            new HistorialTarifario.Asignacion(historica, noviembre, octubre, noviembre),
            new HistorialTarifario.Asignacion(vigente, null, noviembre, null))), AHORA);

        var resultado = consultarJson(COSTOS + "?meses=2&codigoSuministro=000102", sesion);
        assertEquals("000101", resultado.path("codigoSuministro").stringValue());
        assertEquals("PEN", resultado.path("moneda").stringValue());
        assertEquals("1", resultado.path("revisionTarifaria").stringValue());
        assertEquals("2026-11", resultado.at("/actual/periodo").stringValue());
        assertEquals("20000", resultado.at("/actual/consumo/litros").stringValue());
        assertEquals(2, resultado.at("/actual/tarifa/versionTarifa").intValue());
        assertEquals("49.70", resultado.at("/actual/desglose/totalEstimado").stringValue());
        assertEquals("2.000", resultado.at("/actual/desglose/tramos/2/volumenM3").stringValue());
        var anterior = resultado.at("/anteriores/0");
        assertEquals("2026-10", anterior.path("periodo").stringValue());
        assertEquals("ESTIMADO", anterior.path("estado").stringValue());
        assertEquals("12000", anterior.at("/consumo/litros").stringValue());
        assertEquals("2", anterior.at("/consumo/idLecturaFinal").stringValue());
        assertEquals(1, anterior.at("/tarifa/versionTarifa").intValue());
        assertEquals("28.70", anterior.at("/desglose/totalEstimado").stringValue());
        assertEquals("SIN_CONSUMO_CALCULABLE", resultado.at("/anteriores/1/estado").stringValue());
        assertTrue(resultado.at("/anteriores/1/desglose").isNull());
        assertEquals(1, llamadasAgua.get());
        assertEquals(1, llamadasIdentidad.get());
        enviar("GET", COSTOS + "?meses=13", null, sesion, 400);
        enviar("DELETE", CONSULTA + "/sesion", null, sesion, 204);
        enviar("GET", COSTOS, null, sesion, 401);
    }

    @Test
    void costosIdentificanTarifasIncompatiblesYConceptosNoInformados() throws Exception {
        String sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        incorporarLectura("1", "2026-10-01T05:00:00Z", 100);
        incorporarLectura("2", "2026-11-01T05:00:00Z", 5737);
        incorporarLectura("3", "2026-11-10T05:00:00Z", 7632);

        var sinTarifa = consultarJson(COSTOS + "?meses=1", sesion);
        assertEquals("SIN_TARIFA_COMPATIBLE", sinTarifa.at("/actual/estado").stringValue());
        assertTrue(sinTarifa.at("/actual/desglose").isNull());
        assertTrue(sinTarifa.path("revisionTarifaria").isNull());

        var octubre = LocalDate.parse("2026-10-01");
        var mitad = LocalDate.parse("2026-11-15");
        var tarifa = new VersionTarifaria("DOMESTICA", 1, "Demostracion", octubre,
            new BigDecimal("5.00"), null, List.of(tramoCosto("0", null, "1.50", null)));
        tarifas.guardar(new HistorialTarifario("empresa-agua", new CodigoSuministro("000101"), List.of(
            new HistorialTarifario.Asignacion(tarifa, mitad, octubre, mitad))), AHORA);

        var parcial = consultarJson(COSTOS + "?meses=1", sesion);
        assertEquals("SIN_TARIFA_COMPATIBLE", parcial.at("/actual/estado").stringValue());
        assertTrue(parcial.at("/actual/tarifa").isNull());
        assertEquals("CONCEPTOS_INCOMPLETOS", parcial.at("/anteriores/0/estado").stringValue());
        assertEquals("13.46", parcial.at("/anteriores/0/desglose/subtotalConocido").stringValue());
        assertTrue(parcial.at("/anteriores/0/desglose/alcantarillado").isNull());
        assertTrue(parcial.at("/anteriores/0/desglose/igv").isNull());
        assertTrue(parcial.at("/anteriores/0/desglose/totalEstimado").isNull());
    }

    @Test
    void proyeccionUsaLaUltimaMedicionYAplicaLosTramosAlVolumenDelMes() throws Exception {
        enviar("GET", PROYECCION, null, null, 401);
        String sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        agua.set(CAIDA);
        identidad.set(CAIDA);
        incorporarLectura("1", "2026-11-01T05:00:00Z", 100);
        incorporarLectura("2", "2026-11-11T05:00:00Z", 4100);
        jdbc.update("INSERT INTO suministros (codigo, titular, verificado_en_utc) VALUES (?, ?, ?)",
            "000102", "Otro titular ficticio", LocalDateTime.parse("2026-11-01T00:00:00"));
        lectura("empresa-agua", "3", "000102", "2026-11-01T05:00:00", 9000);

        var noviembre = LocalDate.parse("2026-11-01");
        var tarifa = new VersionTarifaria("PROYECCION-PRUEBA", 1, "Demostracion", noviembre,
            new BigDecimal("5.00"), new BigDecimal("18"), List.of(
                tramoCosto("0", "10", "1.50", "0.50"), tramoCosto("10", null, "2.20", "0.80")));
        tarifas.guardar(new HistorialTarifario("empresa-agua", new CodigoSuministro("000101"), List.of(
            new HistorialTarifario.Asignacion(tarifa, null, noviembre, null))), AHORA);

        var resultado = consultarJson(PROYECCION + "?codigoSuministro=000102", sesion);
        assertEquals("000101", resultado.path("codigoSuministro").stringValue());
        assertEquals("America/Lima", resultado.path("zonaHoraria").stringValue());
        assertEquals("2026-11", resultado.path("periodo").stringValue());
        assertEquals(AHORA.toString(), resultado.path("calculadoEn").stringValue());
        assertEquals("PROYECTADO", resultado.path("estado").stringValue());
        assertEquals("RITMO_PROMEDIO_CONSTANTE", resultado.at("/supuestos/metodo").stringValue());
        assertEquals(86400, resultado.at("/supuestos/minimoObservadoSegundos").intValue());
        assertEquals("4000", resultado.at("/observado/litros").stringValue());
        assertEquals("2026-11-11T05:00:00Z", resultado.at("/observado/hasta").stringValue());
        assertEquals("864000", resultado.at("/proyeccion/segundosObservados").stringValue());
        assertEquals("2592000", resultado.at("/proyeccion/segundosDelMes").stringValue());
        assertEquals("12000", resultado.at("/proyeccion/litros").stringValue());
        assertEquals("12.000", resultado.at("/proyeccion/metrosCubicos").stringValue());
        assertEquals("400.000", resultado.at("/proyeccion/promedioLitrosPorDia").stringValue());
        assertEquals("ESTIMADO", resultado.at("/costoProyectado/estado").stringValue());
        assertEquals("1", resultado.at("/costoProyectado/revisionTarifaria").stringValue());
        assertEquals("2.000", resultado.at("/costoProyectado/desglose/tramos/1/volumenM3").stringValue());
        assertEquals("5.00", resultado.at("/costoProyectado/desglose/cargoFijo").stringValue());
        assertEquals("36.58", resultado.at("/costoProyectado/desglose/totalEstimado").stringValue());
        assertEquals(1, llamadasAgua.get());
        assertEquals(1, llamadasIdentidad.get());
        enviar("DELETE", CONSULTA + "/sesion", null, sesion, 204);
        enviar("GET", PROYECCION, null, sesion, 401);
    }

    @Test
    void proyeccionDistingueFaltaDeLecturasDeFaltaDeTarifaYConceptos() throws Exception {
        String sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        var sinLecturas = consultarJson(PROYECCION, sesion);
        assertEquals("SIN_CONSUMO_CALCULABLE", sinLecturas.path("estado").stringValue());
        assertTrue(sinLecturas.path("proyeccion").isNull());
        assertTrue(sinLecturas.path("costoProyectado").isNull());

        incorporarLectura("1", "2026-11-01T05:00:00Z", 100);
        incorporarLectura("2", "2026-11-01T15:00:00Z", 130);
        var pocoTiempo = consultarJson(PROYECCION, sesion);
        assertEquals("TIEMPO_INSUFICIENTE", pocoTiempo.path("estado").stringValue());
        assertEquals("30", pocoTiempo.at("/observado/litros").stringValue());
        assertTrue(pocoTiempo.path("proyeccion").isNull());

        incorporarLectura("3", "2026-11-10T05:00:00Z", 1995);
        var sinTarifa = consultarJson(PROYECCION, sesion);
        assertEquals("6317", sinTarifa.at("/proyeccion/litros").stringValue());
        assertEquals("SIN_TARIFA_COMPATIBLE", sinTarifa.at("/costoProyectado/estado").stringValue());
        assertTrue(sinTarifa.at("/costoProyectado/desglose").isNull());

        var noviembre = LocalDate.parse("2026-11-01");
        var mitad = noviembre.plusDays(14);
        var tarifa = new VersionTarifaria("DOMESTICA", 1, "Demostracion", noviembre,
            new BigDecimal("5.00"), null, List.of(tramoCosto("0", null, "1.50", null)));
        var suministro = new CodigoSuministro("000101");
        tarifas.guardar(new HistorialTarifario("empresa-agua", suministro, List.of(
            new HistorialTarifario.Asignacion(tarifa, null, noviembre, mitad))), AHORA);
        var vence = consultarJson(PROYECCION, sesion);
        assertEquals("6317", vence.at("/proyeccion/litros").stringValue());
        assertEquals("SIN_TARIFA_COMPATIBLE", vence.at("/costoProyectado/estado").stringValue());

        tarifas.guardar(new HistorialTarifario("empresa-agua", suministro, List.of(
            new HistorialTarifario.Asignacion(tarifa, null, noviembre, null))), AHORA);
        var incompleto = consultarJson(PROYECCION, sesion);
        assertEquals("6317", incompleto.at("/proyeccion/litros").stringValue());
        assertEquals("CONCEPTOS_INCOMPLETOS", incompleto.at("/costoProyectado/estado").stringValue());
        assertEquals("2", incompleto.at("/costoProyectado/revisionTarifaria").stringValue());
        assertEquals("14.48", incompleto.at("/costoProyectado/desglose/subtotalConocido").stringValue());
        assertTrue(incompleto.at("/costoProyectado/desglose/totalEstimado").isNull());
    }

    @Test
    void publicacionTarifariaSeUsaEnCostosYProyeccionSinAlterarMesesAnteriores() throws Exception {
        var sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        var admin = iniciarAdminTarifas();
        incorporarLectura("1", "2026-10-01T05:00:00Z", 100);
        incorporarLectura("2", "2026-11-01T05:00:00Z", 1100);
        incorporarLectura("3", "2026-11-11T05:00:00Z", 3100);
        var octubre = LocalDate.parse("2026-10-01");
        var tarifa = new VersionTarifaria("DOMESTICA", 1, "Importada", octubre, new BigDecimal("5.00"),
            null, List.of(tramoCosto("0", null, "1.50", null)));
        var suministro = new CodigoSuministro("000101");
        var historial = new HistorialTarifario("empresa-agua", suministro, List.of(
            new HistorialTarifario.Asignacion(tarifa, null, octubre, null)));
        tarifas.guardar(historial, AHORA);

        var anterior = consultarJson(GESTION_TARIFAS, admin);
        assertEquals("0", anterior.path("revisionAdministrativa").stringValue());
        assertEquals("1", anterior.at("/importada/revision").stringValue());
        enviar("PUT", GESTION_TARIFAS, solicitudTarifa(0, 1, "2026-11-01", null), admin, 200);

        var actualizacion = new HistorialTarifario("empresa-agua", suministro, List.of(
            new HistorialTarifario.Asignacion(tarifa, null, octubre, LocalDate.parse("2027-03-01"))));
        tarifas.guardar(actualizacion, AHORA.plusSeconds(1));
        var costos = consultarJson(COSTOS + "?meses=1", sesion);
        assertEquals("2", costos.path("revisionTarifaria").stringValue());
        assertEquals("1", costos.path("revisionTarifariaAdministrativa").stringValue());
        assertEquals("nivora", costos.at("/actual/tarifa/fuenteTarifaria").stringValue());
        assertEquals("10.00", costos.at("/actual/desglose/totalEstimado").stringValue());
        assertEquals("empresa-agua", costos.at("/anteriores/0/tarifa/fuenteTarifaria").stringValue());
        assertEquals("6.50", costos.at("/anteriores/0/desglose/subtotalConocido").stringValue());
        var proyeccion = consultarJson(PROYECCION, sesion);
        assertEquals("6000", proyeccion.at("/proyeccion/litros").stringValue());
        assertEquals("nivora", proyeccion.at("/costoProyectado/fuenteTarifaria").stringValue());
        assertEquals("20.00", proyeccion.at("/costoProyectado/desglose/totalEstimado").stringValue());

        enviar("PUT", GESTION_TARIFAS, solicitudTarifa(1, 2, "2026-11-15", null), admin, 200);
        assertEquals("SIN_TARIFA_COMPATIBLE",
            consultarJson(COSTOS, sesion).at("/actual/estado").stringValue());
        assertEquals("SIN_TARIFA_COMPATIBLE",
            consultarJson(PROYECCION, sesion).at("/costoProyectado/estado").stringValue());
    }

    @Test
    void publicacionTarifariaConservaLaRevisionYLosExtremosDeLaVigenciaAnterior() throws Exception {
        token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        var admin = iniciarAdminTarifas();
        var primera = solicitudTarifa(0, 1, "2026-11-01", null);
        var segunda = solicitudTarifa(1, 2, "2026-12-01", "2027-01-01");
        enviar("PUT", GESTION_TARIFAS, primera, admin, 200);
        var publicada = json.readTree(enviar("PUT", GESTION_TARIFAS, segunda, admin, 200).body());
        assertEquals("admin", publicada.path("publicadoPor").stringValue());
        assertEquals("2", publicada.at("/historial/revision").stringValue());
        var vigencias = publicada.at("/historial/asignaciones");
        assertEquals(3, vigencias.size());
        assertEquals("2026-12-01", vigencias.at("/0/aplicableHasta").stringValue());
        assertEquals(2, vigencias.at("/1/versionTarifa").intValue());
        assertEquals("2027-01-01", vigencias.at("/2/aplicableDesde").stringValue());
        assertEquals(1, vigencias.at("/2/versionTarifa").intValue());
        assertTrue(vigencias.at("/2/aplicableHasta").isNull());
        var original = consultarJson(GESTION_TARIFAS + "/revisiones/1", admin);
        assertEquals(1, original.at("/historial/asignaciones").size());
        assertTrue(original.at("/historial/asignaciones/0/aplicableHasta").isNull());

        enviar("PUT", GESTION_TARIFAS, segunda, admin, 409);
        var intentoInmutable = primera.replace("\"revisionEsperada\":\"0\"", "\"revisionEsperada\":\"2\"")
            .replace("\"cargoFijo\":5.00", "\"cargoFijo\":6.00");
        enviar("PUT", GESTION_TARIFAS, intentoInmutable, admin, 409);
        assertEquals("2", consultarJson(GESTION_TARIFAS, admin).path("revisionAdministrativa").stringValue());
        enviar("GET", GESTION_TARIFAS + "/revisiones/3", null, admin, 404);
    }

    @Test
    void gestionTarifariaExigePermisoYValidaFechasTramosYPrecision() throws Exception {
        var sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        var primera = solicitudTarifa(0, 1, "2026-11-01", null);
        enviar("GET", GESTION_TARIFAS, null, null, 401);
        enviar("PUT", GESTION_TARIFAS, primera, sesion, 401);
        var admin = iniciarAdminTarifas();
        enviar("GET", GESTION_TARIFAS.replace("000101", "000999"), null, admin, 404);
        enviar("PUT", GESTION_TARIFAS, primera.replace("2026-11-01", "2026-11-31"), admin, 400);
        enviar("PUT", GESTION_TARIFAS, primera.replace("\"desdeM3\":0", "\"desdeM3\":1"), admin, 400);
        enviar("PUT", GESTION_TARIFAS, primera.replace("2.00", "2.00000000000000001"), admin, 400);
        enviar("PUT", GESTION_TARIFAS, primera.replace("\"desdeM3\":0", "\"desdeM3\":0,\"desdeM3\":0"), admin, 400);
        assertEquals("0", consultarJson(GESTION_TARIFAS, admin).path("revisionAdministrativa").stringValue());
        jdbc.update("UPDATE administradores SET cambio_clave_requerido = TRUE WHERE usuario = 'admin'");
        enviar("PUT", GESTION_TARIFAS, primera, admin, 403);
    }

    @Test
    void tarifasImportadasYPublicadasSeAplicanAlCambiarDeMesEnLima() throws Exception {
        var suministro = new CodigoSuministro("000101");
        var noviembre = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        var admin = iniciarAdminTarifas();
        incorporarLectura("1", "2026-11-01T05:00:00Z", 2100);
        incorporarLectura("2", "2026-11-11T05:00:00Z", 6100);
        tarifasProveedor.set(respuestaTarifaria(tarifaImportada(1, "2026-10-01", null, "5.00")));
        assertEquals(1L, incorporarTarifas.ejecutar(suministro).orElseThrow().revision());

        enviar("PUT", GESTION_TARIFAS, solicitudTarifa(0, 1, "2026-12-01", null), admin, 200);
        var costoNoviembre = consultarJson(COSTOS + "?meses=1", noviembre).path("actual");
        assertEquals("empresa-agua", costoNoviembre.at("/tarifa/fuenteTarifaria").stringValue());
        assertEquals("1", costoNoviembre.at("/tarifa/revisionTarifaria").stringValue());
        assertEquals("4000", costoNoviembre.at("/consumo/litros").stringValue());
        assertEquals("15.34", costoNoviembre.at("/desglose/totalEstimado").stringValue());
        var proyeccionNoviembre = consultarJson(PROYECCION, noviembre);
        assertEquals("12000", proyeccionNoviembre.at("/proyeccion/litros").stringValue());
        assertEquals("36.58", proyeccionNoviembre.at("/costoProyectado/desglose/totalEstimado").stringValue());
        enviar("DELETE", CONSULTA + "/sesion", null, noviembre, 204);
        enviar("DELETE", ADMIN + "/sesion", null, admin, 204);

        instantePrueba.set(Instant.parse("2026-12-11T13:00:00Z"));
        var diciembre = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        incorporarLectura("3", "2026-12-01T05:00:00Z", 12100);
        incorporarLectura("4", "2026-12-11T05:00:00Z", 14100);
        tarifasProveedor.set(respuestaTarifaria(
            tarifaImportada(1, "2026-10-01", "2026-12-01", "5.00"),
            tarifaImportada(2, "2026-12-01", null, "9.00")));
        assertEquals(2L, incorporarTarifas.ejecutar(suministro).orElseThrow().revision());
        tarifasProveedor.set(CAIDA);
        assertThrows(IllegalStateException.class, () -> incorporarTarifas.ejecutar(suministro));
        agua.set(CAIDA);
        identidad.set(CAIDA);

        var costos = consultarJson(COSTOS + "?meses=1", diciembre);
        assertEquals("2", costos.path("revisionTarifaria").stringValue());
        assertEquals("1", costos.path("revisionTarifariaAdministrativa").stringValue());
        var cerrado = costos.at("/anteriores/0");
        assertEquals("2026-11", cerrado.path("periodo").stringValue());
        assertEquals("2026-12-01T05:00:00Z", cerrado.path("finMesExclusivo").stringValue());
        assertEquals("3", cerrado.at("/consumo/idLecturaFinal").stringValue());
        assertEquals("10000", cerrado.at("/consumo/litros").stringValue());
        assertEquals("empresa-agua", cerrado.at("/tarifa/fuenteTarifaria").stringValue());
        assertEquals(1, cerrado.at("/tarifa/versionTarifa").intValue());
        assertEquals("29.50", cerrado.at("/desglose/totalEstimado").stringValue());

        var actual = costos.path("actual");
        assertEquals("2026-12", actual.path("periodo").stringValue());
        assertEquals("3", actual.at("/consumo/idLecturaInicial").stringValue());
        assertEquals("4", actual.at("/consumo/idLecturaFinal").stringValue());
        assertEquals("2000", actual.at("/consumo/litros").stringValue());
        assertEquals("nivora", actual.at("/tarifa/fuenteTarifaria").stringValue());
        assertEquals("1", actual.at("/tarifa/revisionTarifaria").stringValue());
        assertEquals("10.00", actual.at("/desglose/totalEstimado").stringValue());

        var proyeccion = consultarJson(PROYECCION, diciembre);
        assertEquals(instantePrueba.get().toString(), proyeccion.path("calculadoEn").stringValue());
        assertEquals("2026-12-11T05:00:00Z", proyeccion.at("/observado/hasta").stringValue());
        assertEquals("RITMO_PROMEDIO_CONSTANTE", proyeccion.at("/supuestos/metodo").stringValue());
        assertEquals("6200", proyeccion.at("/proyeccion/litros").stringValue());
        assertEquals("nivora", proyeccion.at("/costoProyectado/fuenteTarifaria").stringValue());
        assertEquals("1", proyeccion.at("/costoProyectado/revisionTarifaria").stringValue());
        assertEquals("20.50", proyeccion.at("/costoProyectado/desglose/totalEstimado").stringValue());
        assertEquals(3, llamadasTarifas.get());
        assertEquals(2, llamadasAgua.get());
        assertEquals(2, llamadasIdentidad.get());
        enviar("DELETE", CONSULTA + "/sesion", null, diciembre, 204);
    }

    private static Respuesta respuestaTarifaria(String... asignaciones) {
        var cuerpo = """
            {"fuente":"empresa-agua","codigoSuministro":"000101","moneda":"PEN","zonaFechas":"America/Lima",
             "asignaciones":[%s]}
            """.formatted(String.join(",", asignaciones));
        return new Respuesta(200, "application/json", cuerpo);
    }

    private static String tarifaImportada(int version, String desde, String hasta, String cargo) {
        var fin = hasta == null ? "null" : "\"" + hasta + "\"";
        return """
            {"codigoTarifa":"DOMESTICA","versionTarifa":%d,"nombreTarifa":"Tarifa importada",
             "vigenteDesde":"%s","vigenteHasta":%s,"asignadaDesde":"%s","asignadaHasta":null,
             "aplicableDesde":"%s","aplicableHasta":%s,"cargoFijo":%s,"igvPorcentaje":18,
             "tramos":[{"desdeM3":0,"hastaM3":10,"precioPorM3":1.50,"precioAlcantarilladoPorM3":0.50},
                       {"desdeM3":10,"hastaM3":null,"precioPorM3":2.20,"precioAlcantarilladoPorM3":0.80}]}
            """.formatted(version, desde, fin, desde, desde, fin, cargo);
    }

    @Test
    void consultaLosRecibosPropiosEnOrdenYPaginaAunqueLaFuenteEsteCaida() throws Exception {
        var sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        var propio = new CodigoSuministro("000101");
        var ajeno = new CodigoSuministro("000102");
        jdbc.update("INSERT INTO suministros (codigo, titular, verificado_en_utc) VALUES (?, ?, ?)",
            ajeno.valor(), "Otra persona ficticia", LocalDateTime.parse("2026-11-01T00:00:00"));
        var octubre = recibo("REC-101-10", YearMonth.of(2026, 10));
        var septiembre = recibo("REC-101-09", YearMonth.of(2026, 9));
        recibos.guardar(new RecibosEmitidos(propio, List.of(octubre, septiembre)), AHORA);
        recibos.guardar(new RecibosEmitidos(ajeno, List.of(recibo("REC-102-10", YearMonth.of(2026, 10)))), AHORA);
        agua.set(CAIDA);
        identidad.set(CAIDA);

        var primera = consultarJson(RECIBOS + "?limite=1&codigoSuministro=000102", sesion);
        assertEquals("000101", primera.path("codigoSuministro").stringValue());
        assertEquals("PEN", primera.path("moneda").stringValue());
        assertEquals("America/Lima", primera.path("zonaFechas").stringValue());
        assertEquals(1, primera.path("recibos").size());
        var ultimo = primera.at("/recibos/0");
        assertEquals("REC-101-10", ultimo.path("numeroRecibo").stringValue());
        assertEquals("2026-10", ultimo.path("periodo").stringValue());
        assertEquals("5637.125", ultimo.path("consumoLitros").stringValue());
        assertEquals("13.46", ultimo.path("importeTotal").stringValue());
        assertEquals("2026-11-01", ultimo.path("fechaDisponibilidad").stringValue());
        assertEquals("2026-11-08", ultimo.path("fechaVencimiento").stringValue());
        assertEquals("DOMESTICA", ultimo.path("codigoTarifa").stringValue());
        assertEquals(1, ultimo.path("versionTarifa").intValue());
        assertEquals(AHORA.toString(), ultimo.path("recibidoEn").stringValue());
        assertTrue(primera.path("hayMas").booleanValue());
        assertEquals("2026-10", primera.path("siguienteAntesDe").stringValue());

        var anterior = consultarJson(RECIBOS + "?limite=1&antesDe=2026-10", sesion);
        assertEquals("REC-101-09", anterior.at("/recibos/0/numeroRecibo").stringValue());
        assertFalse(anterior.path("hayMas").booleanValue());
        assertTrue(anterior.path("siguienteAntesDe").isNull());
        assertTrue(consultarJson(RECIBOS + "?antesDe=2026-09", sesion).path("recibos").isEmpty());
        var completos = consultarJson(RECIBOS, sesion).path("recibos");
        assertEquals(2, completos.size());
        assertEquals(ultimo, completos.get(0));
        assertEquals(1, llamadasAgua.get());
        assertEquals(1, llamadasIdentidad.get());
    }

    @Test
    void consultaRecibosExigeSesionDeConsultaVigenteYDevuelveListaVacia() throws Exception {
        enviar("GET", RECIBOS, null, null, 401);
        enviar("GET", RECIBOS, null, iniciarAdminTarifas(), 401);
        var sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        var resultado = consultarJson(RECIBOS, sesion);
        assertTrue(resultado.path("recibos").isArray());
        assertTrue(resultado.path("recibos").isEmpty());
        assertFalse(resultado.path("hayMas").booleanValue());
        assertTrue(resultado.path("siguienteAntesDe").isNull());
        enviar("DELETE", CONSULTA + "/sesion", null, sesion, 204);
        enviar("GET", RECIBOS, null, sesion, 401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"limite=0", "limite=61", "limite=abc", "antesDe=2026-13", "antesDe=2026-10-01"})
    void consultaRecibosRechazaParametrosInvalidos(String parametro) throws Exception {
        var sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        var respuesta = enviar("GET", RECIBOS + "?" + parametro, null, sesion, 400);
        assertEquals("Parametros invalidos", json.readTree(respuesta.body()).path("title").stringValue());
    }

    @Test
    void consultaRecibosInformaElFalloDelAlmacenamientoLocal() throws Exception {
        var sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        jdbc.execute("DROP TABLE recibos");
        var respuesta = enviar("GET", RECIBOS, null, sesion, 503);
        assertEquals("Recibos no disponibles", json.readTree(respuesta.body()).path("title").stringValue());
    }

    @Test
    void recibosEmitidosSeSincronizanYSePuedenConsultarDuranteUnaCaida() throws Exception {
        var sesion = token(enviar("POST", CONSULTA + "/sesiones", SOLICITUD, null, 201));
        assertTrue(consultarJson(RECIBOS, sesion).path("recibos").isEmpty());
        var sincronizar = new SincronizarRecibos(programacionRecibos, incorporarRecibos,
            new SincronizarRecibos.Politica(1, 60, Duration.ofSeconds(60),
                Duration.ofSeconds(300), Duration.ofSeconds(30), Duration.ofSeconds(1800)),
            new RelojPrueba(ZoneOffset.UTC));

        var emitidos = new Respuesta(200, "application/json", """
            {"codigoSuministro":"000101","moneda":"PEN","recibos":[
              {"numeroRecibo":"EA-202610-000101","periodo":"2026-10-01","consumoLitros":5637,
               "cargoFijo":5.00,"importeConsumo":8.46,"importeTotal":13.46,
               "fechaDisponibilidad":"2026-11-01","fechaVencimiento":"2026-11-08","codigoTarifa":"DOMESTICA","versionTarifa":1},
              {"numeroRecibo":"EA-202609-000101","periodo":"2026-09-01","consumoLitros":2000,
               "cargoFijo":5.00,"importeConsumo":3.00,"importeTotal":8.00,
               "fechaDisponibilidad":"2026-10-01","fechaVencimiento":"2026-10-08","codigoTarifa":"DOMESTICA","versionTarifa":1}
            ]}
            """);
        recibosProveedor.set(emitidos);
        var primera = sincronizar.ejecutarCiclo().getFirst();
        assertEquals(RepositorioSincronizacionRecibos.Estado.ACTUALIZADO, primera.estado());
        assertTrue(primera.programacionConfirmada());
        assertEquals(new RepositorioRecibos.Incorporacion(2, 0), primera.incorporacion());

        var pagina = consultarJson(RECIBOS + "?limite=1", sesion);
        var octubre = pagina.at("/recibos/0");
        assertEquals("EA-202610-000101", octubre.path("numeroRecibo").stringValue());
        assertEquals("5637.000", octubre.path("consumoLitros").stringValue());
        assertEquals("5.00", octubre.path("cargoFijo").stringValue());
        assertEquals("8.46", octubre.path("importeConsumo").stringValue());
        assertEquals("13.46", octubre.path("importeTotal").stringValue());
        assertEquals("2026-11-01", octubre.path("fechaDisponibilidad").stringValue());
        assertEquals("2026-11-08", octubre.path("fechaVencimiento").stringValue());
        assertEquals(AHORA.toString(), octubre.path("recibidoEn").stringValue());
        assertEquals("2026-10", pagina.path("siguienteAntesDe").stringValue());
        var septiembre = consultarJson(RECIBOS + "?limite=1&antesDe=2026-10", sesion);
        assertEquals("EA-202609-000101", septiembre.at("/recibos/0/numeroRecibo").stringValue());
        assertEquals("8.00", septiembre.at("/recibos/0/importeTotal").stringValue());
        assertFalse(septiembre.path("hayMas").booleanValue());

        recibosProveedor.set(CAIDA);
        agua.set(CAIDA);
        identidad.set(CAIDA);
        instantePrueba.set(primera.proximoIntento());
        var fallo = sincronizar.ejecutarCiclo().getFirst();
        assertEquals(RepositorioSincronizacionRecibos.Estado.ERROR_FUENTE, fallo.estado());
        assertEquals(primera.proximoIntento().plusSeconds(30), fallo.proximoIntento());
        assertTrue(sincronizar.ejecutarCiclo().isEmpty());
        assertEquals(pagina, consultarJson(RECIBOS + "?limite=1", sesion));

        recibosProveedor.set(emitidos);
        instantePrueba.set(fallo.proximoIntento());
        var recuperado = sincronizar.ejecutarCiclo().getFirst();
        assertEquals(RepositorioSincronizacionRecibos.Estado.ACTUALIZADO, recuperado.estado());
        assertEquals(new RepositorioRecibos.Incorporacion(0, 2), recuperado.incorporacion());
        assertEquals(pagina, consultarJson(RECIBOS + "?limite=1", sesion));
        assertEquals(septiembre, consultarJson(RECIBOS + "?limite=1&antesDe=2026-10", sesion));
        assertEquals(2, recibos.buscarRecientes(new CodigoSuministro("000101"), 60).size());
        assertEquals(3, llamadasRecibos.get());
        assertEquals(1, llamadasAgua.get());
        assertEquals(1, llamadasIdentidad.get());
        enviar("DELETE", CONSULTA + "/sesion", null, sesion, 204);
        enviar("GET", RECIBOS, null, sesion, 401);
    }

    private static ReciboEmitido recibo(String numero, YearMonth periodo) {
        var disponible = periodo.plusMonths(1).atDay(1);
        return new ReciboEmitido(numero, periodo, new BigDecimal("5637.125"), new BigDecimal("5.00"),
            new BigDecimal("8.46"), new BigDecimal("13.46"), disponible, disponible.plusDays(7), "DOMESTICA", 1);
    }

    private String iniciarAdminTarifas() throws Exception {
        inicializar.ejecutar("admin", "Admin de prueba", CLAVE_ADMIN.toCharArray());
        return token(enviar("POST", ADMIN + "/sesiones",
            "{\"usuario\":\"admin\",\"clave\":\"" + CLAVE_ADMIN + "\"}", null, 201));
    }

    private static String solicitudTarifa(long revision, int version, String desde, String hasta) {
        return """
            {"revisionEsperada":"%d","codigoTarifa":"DOMESTICA","versionTarifa":%d,"nombreTarifa":"Tarifa de prueba",
             "vigenteDesde":"%s","asignadaDesde":"%s","asignadaHasta":%s,"cargoFijo":5.00,"igvPorcentaje":0,
             "tramos":[{"desdeM3":0,"hastaM3":null,"precioAguaPorM3":2.00,"precioAlcantarilladoPorM3":0.50}]}
            """.formatted(revision, version, desde, desde, hasta == null ? "null" : "\"" + hasta + "\"");
    }

    private static VersionTarifaria.Tramo tramoCosto(String desde, String hasta, String agua, String alcantarillado) {
        return new VersionTarifaria.Tramo(new BigDecimal(desde), hasta == null ? null : new BigDecimal(hasta),
            new BigDecimal(agua), alcantarillado == null ? null : new BigDecimal(alcantarillado));
    }

    private JsonNode consultarJson(String ruta, String sesion) throws Exception {
        return json.readTree(enviar("GET", ruta, null, sesion, 200).body());
    }

    private void incorporarLectura(String id, String fecha, long litros) {
        String fuente = "empresa-agua";
        var codigo = new CodigoSuministro("000101");
        var lectura = new LecturaAcumulada(fuente, id, codigo, "1", Instant.parse(fecha), litros);
        var lote = new LoteLecturas(fuente, codigo, List.of(lectura), id, false);
        assertEquals(new ResultadoIncorporacion(1, 0, 0),
            repositorioLecturas.incorporar(lote, instantePrueba.get()));
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
            servidor.createContext("/api/v1/suministros/000101/tarifas", intercambio -> {
                llamadasTarifas.incrementAndGet();
                responder(intercambio, tarifasProveedor.get());
            });
            servidor.createContext("/api/v1/suministros/000101/recibos", intercambio -> {
                llamadasRecibos.incrementAndGet();
                responder(intercambio, recibosProveedor.get());
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

    private static final class RelojPrueba extends Clock {
        private final ZoneId zona;

        RelojPrueba(ZoneId zona) {
            this.zona = zona;
        }

        @Override public ZoneId getZone() { return zona; }
        @Override public Clock withZone(ZoneId zona) { return new RelojPrueba(zona); }
        @Override public Instant instant() { return instantePrueba.get(); }
    }

    private record Respuesta(int estado, String tipo, String cuerpo) {}

    private record PeticionIdentidad(String metodo, String ruta, String consulta, String autorizacion) {}
}
