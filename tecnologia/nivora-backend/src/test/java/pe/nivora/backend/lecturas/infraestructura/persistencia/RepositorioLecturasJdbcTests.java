package pe.nivora.backend.lecturas.infraestructura.persistencia;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import tools.jackson.databind.json.JsonMapper;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.LecturaAcumulada;
import pe.nivora.backend.lecturas.dominio.LoteLecturas;
import pe.nivora.backend.lecturas.dominio.ResultadoIncorporacion;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturasNoDisponibleException;
import pe.nivora.backend.lecturas.dominio.contratos.SuministroNoRegistradoException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositorioLecturasJdbcTests {
    private static final CodigoSuministro CODIGO = new CodigoSuministro("13100390");
    private static final CodigoSuministro OTRO = new CodigoSuministro("13100391");
    private static final Instant BASE = Instant.parse("2026-10-01T05:00:00.123456Z");
    private static final Instant RECEPCION = Instant.parse("2026-11-09T20:00:00.654321Z");
    private JdbcTemplate jdbc;
    private RepositorioLecturasJdbc repositorio;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void prepararBaseAisladaConLosScriptsReales() {
        var fuente = new DriverManagerDataSource("jdbc:h2:mem:lecturas_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
            + ";INIT=CREATE SCHEMA IF NOT EXISTS nivora\\;SET SCHEMA nivora", "sa", "");
        jdbc = new JdbcTemplate(fuente);
        new ResourceDatabasePopulator(new FileSystemResource("base-datos/02-suministros.sql"),
            new FileSystemResource("base-datos/03-lecturas.sql")).execute(fuente);
        jdbc.update("INSERT INTO suministros VALUES (?, ?, ?)", CODIGO.valor(), "Titular ficticio",
            LocalDateTime.parse("2026-11-09T19:00:00"));
        jdbc.update("INSERT INTO suministros VALUES (?, ?, ?)", OTRO.valor(), "Otro titular ficticio",
            LocalDateTime.parse("2026-11-09T19:00:00"));
        repositorio = new RepositorioLecturasJdbc(jdbc, new DataSourceTransactionManager(fuente), json);
    }

    @AfterEach
    void cerrarBase() {
        jdbc.execute("SHUTDOWN");
    }

    @Test
    void laBaseEmpiezaSinLecturasNiIncidencias() {
        assertTrue(repositorio.buscar("empresa-agua", "1").isEmpty());
        assertEquals(0, repositorio.contar(CODIGO));
        assertEquals(0, repositorio.contarIncidencias(CODIGO));
    }

    @Test
    void guardaUnaLecturaCompletaYConservaSuFechaUtc() {
        var lectura = lectura("9007199254740993", "391", BASE, 920231);
        assertEquals(new ResultadoIncorporacion(1, 0, 0), incorporar(lectura));
        assertEquals(lectura, repositorio.buscar("empresa-agua", lectura.idLectura()).orElseThrow());
        assertEquals("L", jdbc.queryForObject("SELECT unidad FROM lecturas", String.class));
        assertEquals("ACUMULADA", jdbc.queryForObject("SELECT tipo_medicion FROM lecturas", String.class));
        assertEquals(LocalDateTime.parse("2026-10-01T05:00:00.123456"),
            jdbc.queryForObject("SELECT fecha_medicion_utc FROM lecturas", LocalDateTime.class));
    }

    @Test
    void repetirUnaLecturaNoLaCuentaOtraVezNiActualizaLaRecepcionOriginal() {
        var lectura = lectura("1", "391", BASE, 100);
        incorporar(lectura);

        assertEquals(new ResultadoIncorporacion(0, 1, 0),
            repositorio.incorporar(lote(lectura), RECEPCION.plusSeconds(60)));
        assertEquals(1, repositorio.contar(CODIGO));
        assertEquals(0, repositorio.contarIncidencias(CODIGO));
        assertEquals(LocalDateTime.parse("2026-11-09T20:00:00.654321"),
            jdbc.queryForObject("SELECT recibida_en_utc FROM lecturas", LocalDateTime.class));
    }

    @ParameterizedTest
    @MethodSource("cambiosConElMismoId")
    void unIdConOtrosDatosGeneraIncidenciaSinReemplazarElAceptado(LecturaAcumulada distinta) {
        var original = lectura("1", "391", BASE, 100);
        incorporar(original);

        assertEquals(new ResultadoIncorporacion(0, 0, 1), incorporar(distinta));
        assertEquals(original, repositorio.buscar("empresa-agua", "1").orElseThrow());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lecturas", Long.class));
        assertEquals("ID_EN_CONFLICTO", motivo());
    }

    static Stream<LecturaAcumulada> cambiosConElMismoId() {
        return Stream.of(lectura("1", "391", BASE, 101),
            lectura("1", "391", BASE.plusSeconds(1), 100),
            lectura("1", "999", BASE, 100),
            new LecturaAcumulada("empresa-agua", "1", OTRO, "391", BASE, 100));
    }

    @Test
    void repetirElMismoConflictoConservaUnaSolaIncidenciaYElDetalleRecibido() {
        incorporar(lectura("1", "391", BASE, 100));
        var distinta = lectura("1", "391", BASE, 101);
        incorporar(distinta);
        repositorio.incorporar(lote(distinta), RECEPCION.plusSeconds(60));

        assertEquals(1, repositorio.contarIncidencias(CODIGO));
        assertEquals(2, jdbc.queryForObject("SELECT repeticiones FROM incidencias_lecturas", Long.class));
        var detalle = json.readTree(jdbc.queryForObject(
            "SELECT datos_recibidos FROM incidencias_lecturas", String.class));
        assertEquals(101, detalle.get("valorLitros").longValue());
        assertEquals(BASE.toString(), detalle.get("fechaMedicion").stringValue());
    }

    @Test
    void datosRechazadosDiferentesConservanIncidenciasDiferentes() {
        incorporar(lectura("1", "391", BASE, 100));
        incorporar(lectura("1", "391", BASE, 101));
        incorporar(lectura("1", "391", BASE, 102));
        assertEquals(2, repositorio.contarIncidencias(CODIGO));
    }

    @Test
    void otraIdentificacionParaLaMismaMedicionNoCreaOtraLectura() {
        incorporar(lectura("1", "391", BASE, 100));

        assertEquals(new ResultadoIncorporacion(0, 0, 1), incorporar(lectura("2", "391", BASE, 100)));
        assertEquals(1, repositorio.contar(CODIGO));
        assertEquals("MEDICION_CON_OTRO_ID", motivo());
    }

    @ParameterizedTest
    @MethodSource("fechasRechazadas")
    void conservaElMotivoYLaFechaExactaDeUnaLecturaRechazada(Instant fecha, String esperado) {
        assertEquals(new ResultadoIncorporacion(0, 0, 1), incorporar(lectura("1", "391", fecha, 100)));
        assertEquals(0, repositorio.contar(CODIGO));
        assertEquals(esperado, motivo());
        var detalle = json.readTree(jdbc.queryForObject(
            "SELECT datos_recibidos FROM incidencias_lecturas", String.class));
        assertEquals(fecha.toString(), detalle.get("fechaMedicion").stringValue());
    }

    static Stream<Arguments> fechasRechazadas() {
        return Stream.of(Arguments.of(RECEPCION.plusSeconds(1), "FECHA_POSTERIOR_A_RECEPCION"),
            Arguments.of(BASE.plusNanos(1), "FECHA_PRECISION_NO_SOPORTADA"),
            Arguments.of(Instant.parse("0999-01-01T00:00:00Z"), "FECHA_FUERA_DE_RANGO"),
            Arguments.of(Instant.MAX, "FECHA_FUERA_DE_RANGO"));
    }

    @Test
    void rechazaUnContadorQueRetrocedeEnElMismoMedidor() {
        incorporar(lectura("1", "391", BASE, 100));
        assertEquals(new ResultadoIncorporacion(0, 0, 1),
            incorporar(lectura("2", "391", BASE.plusSeconds(60), 99)));
        assertEquals("CONTADOR_INCONSISTENTE", motivo());
    }

    @Test
    void rechazaUnaLecturaAnteriorQueSuperaElContadorPosteriorAceptado() {
        incorporar(lectura("2", "391", BASE.plusSeconds(60), 200));
        assertEquals(new ResultadoIncorporacion(0, 0, 1), incorporar(lectura("1", "391", BASE, 201)));
        assertEquals("CONTADOR_INCONSISTENTE", motivo());
    }

    @Test
    void admiteUnaLecturaHistoricaCompatibleEntreDosAceptadas() {
        incorporar(lectura("1", "391", BASE, 100),
            lectura("3", "391", BASE.plusSeconds(120), 200));
        assertEquals(new ResultadoIncorporacion(1, 0, 0),
            incorporar(lectura("2", "391", BASE.plusSeconds(60), 150)));
        assertEquals(3, repositorio.contar(CODIGO));
    }

    @Test
    void admiteContadoresIgualesEnInstantesDistintos() {
        assertEquals(new ResultadoIncorporacion(2, 0, 0), incorporar(
            lectura("1", "391", BASE, 100), lectura("2", "391", BASE.plusSeconds(60), 100)));
    }

    @Test
    void unMedidorDistintoPuedeComenzarEnCero() {
        assertEquals(new ResultadoIncorporacion(2, 0, 0), incorporar(
            lectura("1", "391", BASE, 100), lectura("2", "999", BASE, 0)));
    }

    @Test
    void unRechazoDeDatosNoImpideConservarLasOtrasLecturasValidasDelLote() {
        assertEquals(new ResultadoIncorporacion(2, 0, 1), incorporar(
            lectura("1", "391", BASE, 100),
            lectura("2", "391", BASE.plusSeconds(60), 99),
            lectura("3", "391", BASE.plusSeconds(120), 120)));
        assertEquals(2, repositorio.contar(CODIGO));
        assertTrue(repositorio.buscar("empresa-agua", "2").isEmpty());
        assertEquals(1, repositorio.contarIncidencias(CODIGO));
    }

    @Test
    void unaLecturaRechazadaPuedeIncorporarseSiDespuesLlegaCorregida() {
        incorporar(lectura("1", "391", BASE, 100));
        incorporar(lectura("2", "391", BASE.plusSeconds(60), 99));

        assertEquals(new ResultadoIncorporacion(1, 0, 0),
            incorporar(lectura("2", "391", BASE.plusSeconds(60), 110)));
        assertEquals(2, repositorio.contar(CODIGO));
        assertEquals(1, repositorio.contarIncidencias(CODIGO));
        assertEquals(110, repositorio.buscar("empresa-agua", "2").orElseThrow().valorLitros());
    }

    @Test
    void dosFuentesPuedenUsarElMismoId() {
        incorporar(lectura("1", "391", BASE, 100));
        var otraFuente = new LecturaAcumulada("otra-fuente", "1", CODIGO, "391", BASE, 200);
        assertEquals(new ResultadoIncorporacion(1, 0, 0), incorporar(otraFuente));
        assertEquals(2, repositorio.contar(CODIGO));
        assertEquals(otraFuente, repositorio.buscar("otra-fuente", "1").orElseThrow());
    }

    @Test
    void laIdentidadTextualDistingueMayusculasYMinusculas() {
        assertEquals(new ResultadoIncorporacion(2, 0, 0), incorporar(
            lectura("A", "391", BASE, 100), lectura("a", "391", BASE.plusSeconds(60), 101)));
        assertEquals(2, repositorio.contar(CODIGO));
    }

    @Test
    void exigeQueElSuministroEsteRegistradoYRevierteElControlCreado() {
        var desconocida = new LecturaAcumulada("empresa-agua", "1",
            new CodigoSuministro("99999999"), "391", BASE, 100);
        assertThrows(SuministroNoRegistradoException.class, () -> incorporar(desconocida));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM control_incorporacion_lecturas", Long.class));
        assertEquals(0, repositorio.contar(CODIGO));
    }

    @Test
    void unaPaginaVaciaNoGeneraLecturas() {
        var vacio = new LoteLecturas("empresa-agua", CODIGO, java.util.List.of(), "100391", false);
        assertEquals(new ResultadoIncorporacion(0, 0, 0), repositorio.incorporar(vacio, RECEPCION));
        assertEquals(0, repositorio.contar(CODIGO));
    }

    @Test
    void unFalloAlGuardarUnaIncidenciaRevierteLasLecturasNuevasDelMismoLote() {
        incorporar(lectura("9", "391", BASE, 100));
        jdbc.execute("DROP TABLE incidencias_lecturas");

        assertThrows(RepositorioLecturasNoDisponibleException.class, () -> incorporar(
            lectura("2", "391", BASE.plusSeconds(60), 200), lectura("9", "391", BASE, 101)));
        assertEquals(1, repositorio.contar(CODIGO));
        assertTrue(repositorio.buscar("empresa-agua", "2").isEmpty());
        assertEquals(100, repositorio.buscar("empresa-agua", "9").orElseThrow().valorLitros());
    }

    @Test
    void dosIncorporacionesSimultaneasNoDuplicanLaLectura() throws Exception {
        var inicio = new CountDownLatch(1);
        var lote = lote(lectura("1", "391", BASE, 100));
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var primera = ejecutor.submit(() -> { inicio.await(); return repositorio.incorporar(lote, RECEPCION); });
            var segunda = ejecutor.submit(() -> { inicio.await(); return repositorio.incorporar(lote, RECEPCION); });
            inicio.countDown();
            var a = primera.get(10, TimeUnit.SECONDS);
            var b = segunda.get(10, TimeUnit.SECONDS);
            assertEquals(1, a.nuevas() + b.nuevas());
            assertEquals(1, a.repetidas() + b.repetidas());
            assertEquals(0, a.rechazadas() + b.rechazadas());
            assertEquals(1, repositorio.contar(CODIGO));
        }
    }

    @Test
    void unFalloDeConsultaNoSePresentaComoUnaLecturaInexistente() {
        jdbc.execute("DROP TABLE lecturas");
        assertThrows(RepositorioLecturasNoDisponibleException.class,
            () -> repositorio.buscar("empresa-agua", "1"));
    }

    private ResultadoIncorporacion incorporar(LecturaAcumulada... lecturas) {
        return repositorio.incorporar(lote(lecturas), RECEPCION);
    }

    private static LoteLecturas lote(LecturaAcumulada... lecturas) {
        var primera = lecturas[0];
        return new LoteLecturas(primera.fuente(), primera.suministro(), Arrays.asList(lecturas),
            lecturas[lecturas.length - 1].idLectura(), false);
    }

    private static LecturaAcumulada lectura(String id, String medidor, Instant fecha, long valor) {
        return new LecturaAcumulada("empresa-agua", id, CODIGO, medidor, fecha, valor);
    }

    private String motivo() {
        return jdbc.queryForObject("SELECT motivo FROM incidencias_lecturas", String.class);
    }
}
