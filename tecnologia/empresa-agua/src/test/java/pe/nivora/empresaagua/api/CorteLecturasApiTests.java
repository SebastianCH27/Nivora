package pe.nivora.empresaagua.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:empresa_corte_api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
@ActiveProfiles("test")
class CorteLecturasApiTests {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private JsonMapper json;
    @Value("${local.server.port}") private int puerto;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3)).build();

    @BeforeEach
    void preparar() {
        jdbc.execute("DROP TABLE IF EXISTS lecturas");
        jdbc.execute("DROP TABLE IF EXISTS medidores");
        jdbc.execute("DROP TABLE IF EXISTS suministros");
        jdbc.execute("DROP TABLE IF EXISTS control_emision_lecturas");
        jdbc.execute("CREATE TABLE suministros (id_suministro BIGINT PRIMARY KEY, codigo VARCHAR(20) UNIQUE)");
        jdbc.execute("""
            CREATE TABLE medidores (
                id_medidor BIGINT PRIMARY KEY, id_suministro BIGINT NOT NULL,
                fecha_instalacion DATE, fecha_retiro DATE,
                UNIQUE(id_medidor, id_suministro),
                FOREIGN KEY(id_suministro) REFERENCES suministros(id_suministro))
            """);
        jdbc.execute("""
            CREATE TABLE lecturas (
                id_lectura BIGINT PRIMARY KEY, id_suministro BIGINT NOT NULL,
                id_medidor BIGINT NOT NULL, fecha_medicion DATETIME(6) NOT NULL,
                lectura_acumulada_m3 DECIMAL(15,3) NOT NULL,
                FOREIGN KEY(id_medidor,id_suministro) REFERENCES medidores(id_medidor,id_suministro))
            """);
        jdbc.execute("CREATE INDEX idx_lecturas_suministro_id ON lecturas(id_suministro,id_lectura)");
        jdbc.execute("CREATE TABLE control_emision_lecturas(id_control TINYINT PRIMARY KEY, ultima_emision_utc DATETIME(6))");
        jdbc.update("INSERT INTO suministros VALUES (1,'13100390'),(2,'13100391'),(3,'00001234')");
        jdbc.update("INSERT INTO medidores VALUES (10,1,'2026-10-01',NULL),(11,1,'2026-09-01','2026-10-01'),(20,2,'2026-10-01',NULL)");
        jdbc.update("INSERT INTO lecturas VALUES (10,1,10,'2026-10-01 05:00:00',100),(20,1,10,'2026-10-02 05:00:00',100.100),(30,1,10,'2026-10-03 05:00:00',100.200),(90,2,20,'2026-10-01 05:00:00',12)");
        jdbc.update("INSERT INTO control_emision_lecturas VALUES (1,'2026-11-01 17:00:00.123456')");
    }

    @Test
    void corteIncluyeLimiteGlobalComoTextoYFechaUtc() throws Exception {
        JsonNode cuerpo = get("/api/v1/lecturas/corte", 200);
        assertEquals("empresa-agua", cuerpo.get("fuente").stringValue());
        assertEquals("90", cuerpo.get("hastaIdLectura").stringValue());
        assertEquals("2026-11-01T17:00:00.123456Z", cuerpo.get("confirmadoHasta").stringValue());
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM lecturas", Integer.class));
    }

    @Test
    void conservaIdentificadoresSuperioresAlEnteroExactoDeJavascript() throws Exception {
        jdbc.update("INSERT INTO lecturas VALUES (9007199254740993,1,10,'2026-10-04 05:00:00',100.300)");
        assertEquals("9007199254740993", get("/api/v1/lecturas/corte",200).get("hastaIdLectura").stringValue());
    }

    @Test
    void corteVacioConservaCeroComoTexto() throws Exception {
        jdbc.update("DELETE FROM lecturas");
        assertEquals("0", get("/api/v1/lecturas/corte",200).get("hastaIdLectura").stringValue());
    }

    @Test
    void primeraEmisionPendienteNoInventaUnCorte() throws Exception {
        jdbc.update("UPDATE control_emision_lecturas SET ultima_emision_utc=NULL");
        get("/api/v1/lecturas/corte",503);
    }

    @Test
    void controlAusenteNoInventaUnCorte() throws Exception {
        jdbc.update("DELETE FROM control_emision_lecturas");
        get("/api/v1/lecturas/corte",503);
    }

    @Test
    void errorSqlDelCorteNoExponeLaConsulta() throws Exception {
        jdbc.execute("DROP TABLE control_emision_lecturas");
        JsonNode cuerpo=get("/api/v1/lecturas/corte",503);
        assertFalse(cuerpo.toString().contains("control_emision_lecturas"));
    }

    @Test
    void paginaConLimiteExcluyeLasAltasPosteriores() throws Exception {
        String limite=get("/api/v1/lecturas/corte",200).get("hastaIdLectura").stringValue();
        JsonNode primera=get(ruta("?despuesDe=0&limite=1&hastaId="+limite),200);
        assertEquals("10",primera.get("siguienteDespuesDe").stringValue());
        assertTrue(primera.get("hayMas").booleanValue());
        jdbc.update("INSERT INTO lecturas VALUES (100,1,10,'2026-11-01 18:00:00',100.400)");
        jdbc.update("UPDATE control_emision_lecturas SET ultima_emision_utc='2026-11-01 18:00:00'");
        JsonNode segunda=get(ruta("?despuesDe=10&limite=2&hastaId="+limite),200);
        assertEquals(2,segunda.get("lecturas").size());
        assertEquals("30",segunda.get("siguienteDespuesDe").stringValue());
        assertFalse(segunda.get("hayMas").booleanValue());
        assertEquals("100",get("/api/v1/lecturas/corte",200).get("hastaIdLectura").stringValue());
        assertEquals(segunda,get(ruta("?despuesDe=10&limite=2&hastaId="+limite),200));
    }

    @Test
    void consultaAnteriorSinHastaIdConservaSuContrato() throws Exception {
        jdbc.update("INSERT INTO lecturas VALUES (100,1,10,'2026-11-01 18:00:00',100.400)");
        JsonNode todas=get(ruta("?limite=500"),200);
        assertEquals(4,todas.get("lecturas").size());
        assertEquals("100",todas.get("siguienteDespuesDe").stringValue());
        assertFalse(todas.get("hayMas").booleanValue());
    }

    @Test
    void limiteCeroDevuelvePaginaVaciaParaSuministroExistente() throws Exception {
        JsonNode pagina=get(ruta("?hastaId=0"),200);
        assertEquals(0,pagina.get("lecturas").size());
        assertEquals("0",pagina.get("siguienteDespuesDe").stringValue());
        assertFalse(pagina.get("hayMas").booleanValue());
    }

    @Test
    void cursorEnElLimiteDevuelvePaginaVacia() throws Exception {
        JsonNode pagina=get(ruta("?despuesDe=20&hastaId=20"),200);
        assertEquals(0,pagina.get("lecturas").size());
        assertEquals("20",pagina.get("siguienteDespuesDe").stringValue());
        assertFalse(pagina.get("hayMas").booleanValue());
    }

    @ParameterizedTest
    @ValueSource(strings={"?hastaId=-1","?hastaId=ABC","?hastaId=9223372036854775808","?despuesDe=30&hastaId=20","?hastaId=","?hastaId=00"})
    void limiteInvalidoDevuelve400(String parametros) throws Exception {
        get(ruta(parametros),400);
    }

    @Test
    void inventarioIncluyeMedidoresAnterioresYActualesOrdenados() throws Exception {
        JsonNode cuerpo=get("/api/v1/suministros/13100390/medidores",200);
        assertEquals("empresa-agua",cuerpo.get("fuente").stringValue());
        assertEquals("13100390",cuerpo.get("codigoSuministro").stringValue());
        assertEquals("America/Lima",cuerpo.get("zonaFechas").stringValue());
        JsonNode lista=cuerpo.get("medidores");
        assertEquals(2,lista.size());
        assertEquals("11",lista.get(0).get("idMedidor").stringValue());
        assertEquals("2026-09-01",lista.get(0).get("fechaInstalacion").stringValue());
        assertEquals("2026-10-01",lista.get(0).get("fechaRetiro").stringValue());
        assertEquals("10",lista.get(1).get("idMedidor").stringValue());
        assertTrue(lista.get(1).get("fechaRetiro").isNull());
    }

    @Test
    void inventarioNoMezclaLosMedidoresDeOtrosSuministros() throws Exception {
        JsonNode lista=get("/api/v1/suministros/13100391/medidores",200).get("medidores");
        assertEquals(1,lista.size());
        assertEquals("20",lista.get(0).get("idMedidor").stringValue());
    }

    @Test
    void inventarioSinMedidoresInformaIndisponibilidad() throws Exception {
        get("/api/v1/suministros/00001234/medidores",503);
    }

    @Test
    void inventarioRespetaCerosIniciales() throws Exception {
        jdbc.update("INSERT INTO medidores VALUES (30,3,'2026-10-01',NULL)");
        assertEquals("00001234",get("/api/v1/suministros/00001234/medidores",200).get("codigoSuministro").stringValue());
    }

    @ParameterizedTest
    @ValueSource(strings={"ABC","123456789012345678901"})
    void inventarioValidaElCodigo(String codigo) throws Exception {
        get("/api/v1/suministros/"+codigo+"/medidores",400);
    }

    @Test
    void suministroAusenteDevuelve404EnAmbasConsultas() throws Exception {
        get("/api/v1/suministros/99999999/medidores",404);
        get("/api/v1/suministros/99999999/lecturas?hastaId=90",404);
    }

    @Test
    void inventarioNoAceptaFechasInvertidas() throws Exception {
        jdbc.update("UPDATE medidores SET fecha_retiro='2026-09-30' WHERE id_medidor=10");
        get("/api/v1/suministros/13100390/medidores",503);
    }

    @Test
    void inventarioNoTruncaSilenciosamenteUnaListaExcesiva() throws Exception {
        for (int n=100;n<1100;n++) jdbc.update("INSERT INTO medidores VALUES (?,1,'2026-10-01',NULL)",n);
        get("/api/v1/suministros/13100390/medidores",503);
    }

    private String ruta(String parametros) {
        return "/api/v1/suministros/13100390/lecturas"+parametros;
    }

    private JsonNode get(String ruta,int estado) throws Exception {
        var peticion=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+ruta))
            .timeout(Duration.ofSeconds(15)).GET().build();
        var respuesta=http.send(peticion,HttpResponse.BodyHandlers.ofString());
        assertEquals(estado,respuesta.statusCode(),respuesta.body());
        assertEquals("no-store",respuesta.headers().firstValue("Cache-Control").orElse(""));
        if (estado!=200) assertTrue(respuesta.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
        return json.readTree(respuesta.body());
    }
}
