package pe.nivora.backend.acceso.infraestructura.persistencia;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import pe.nivora.backend.acceso.aplicacion.ActualizarSuministro;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.Suministro;
import pe.nivora.backend.acceso.dominio.SuministroVerificado;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministros;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministrosNoDisponibleException;
import pe.nivora.backend.acceso.dominio.contratos.RepositorioSuministrosNoDisponibleException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositorioSuministrosJdbcTests {

    private static final CodigoSuministro CODIGO = new CodigoSuministro("13100390");
    private static final Instant FECHA = Instant.parse("2026-11-09T18:00:00.123456Z");

    private SingleConnectionDataSource fuente;
    private JdbcTemplate jdbc;
    private RepositorioSuministrosJdbc repositorio;

    @BeforeEach
    void crearBaseAisladaConElScriptDelProyecto() {
        fuente = new SingleConnectionDataSource(
            "jdbc:h2:mem:suministros_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "", true);
        jdbc = new JdbcTemplate(fuente);
        jdbc.execute("CREATE SCHEMA nivora");
        new ResourceDatabasePopulator(
            new FileSystemResource("base-datos/02-suministros.sql")).execute(fuente);
        repositorio = new RepositorioSuministrosJdbc(jdbc);
    }

    @AfterEach
    void cerrarBase() {
        fuente.destroy();
    }

    @Test
    void devuelveVacioCuandoNoHayDatosGuardados() {
        assertTrue(repositorio.buscar(CODIGO).isEmpty());
    }

    @Test
    void conservaDatosUtf8YMicrosegundosEnLaBase() {
        var verificado = verificacion(CODIGO, "María Pérez Torres", FECHA);
        repositorio.guardar(verificado);

        var otraInstancia = new RepositorioSuministrosJdbc(new JdbcTemplate(fuente));
        assertEquals(verificado, otraInstancia.buscar(CODIGO).orElseThrow());
        assertEquals(LocalDateTime.parse("2026-11-09T18:00:00.123456"),
            jdbc.queryForObject("SELECT verificado_en_utc FROM suministros",
                LocalDateTime.class));
    }

    @Test
    void repetirUnaVerificacionNoDuplicaElSuministro() {
        var verificado = verificacion(CODIGO, "Titular de prueba", FECHA);
        repositorio.guardar(verificado);
        repositorio.guardar(verificado);
        repositorio.guardar(verificado);

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM suministros", Integer.class));
        assertEquals(verificado, repositorio.buscar(CODIGO).orElseThrow());
    }

    @Test
    void actualizaTitularYFechaSinCrearOtraFila() {
        repositorio.guardar(verificacion(CODIGO, "Titular anterior", FECHA));
        var reciente = verificacion(CODIGO, "Titular actualizado", FECHA.plusSeconds(60));

        repositorio.guardar(reciente);

        assertEquals(reciente, repositorio.buscar(CODIGO).orElseThrow());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM suministros", Integer.class));
    }

    @Test
    void unaEscrituraRetrasadaNoReemplazaUnaVerificacionPosterior() {
        var reciente = verificacion(CODIGO, "Titular reciente", FECHA.plusSeconds(60));
        repositorio.guardar(reciente);

        repositorio.guardar(verificacion(CODIGO, "Titular anterior", FECHA));

        assertEquals(reciente, repositorio.buscar(CODIGO).orElseThrow());
    }

    @Test
    void conservaLosCerosInicialesComoParteDeLaIdentidad() {
        var conCeros = new CodigoSuministro("00123");
        var sinCeros = new CodigoSuministro("123");
        repositorio.guardar(verificacion(conCeros, "Titular A", FECHA));
        repositorio.guardar(verificacion(sinCeros, "Titular B", FECHA));

        assertEquals("Titular A", repositorio.buscar(conCeros).orElseThrow().suministro().titular());
        assertEquals("Titular B", repositorio.buscar(sinCeros).orElseThrow().suministro().titular());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM suministros", Integer.class));
    }

    @Test
    void conservaLaInformacionAceptadaSiUnaActualizacionExternaFalla() {
        var caida = new AtomicBoolean(false);
        FuenteSuministros fuenteExterna = codigo -> {
            if (caida.get()) {
                throw new FuenteSuministrosNoDisponibleException("Caida simulada.");
            }
            return Optional.of(new Suministro(codigo, "Titular verificado"));
        };
        var actualizar = new ActualizarSuministro(fuenteExterna, repositorio,
            Clock.fixed(FECHA, ZoneOffset.UTC));
        var aceptado = actualizar.ejecutar(CODIGO).orElseThrow();
        caida.set(true);

        assertThrows(FuenteSuministrosNoDisponibleException.class,
            () -> actualizar.ejecutar(CODIGO));
        assertEquals(aceptado, repositorio.buscar(CODIGO).orElseThrow());
    }

    @Test
    void unaAusenciaPosteriorNoBorraElRegistroHistoricamenteAceptado() {
        var aceptado = verificacion(CODIGO, "Titular guardado", FECHA);
        repositorio.guardar(aceptado);
        var actualizar = new ActualizarSuministro(codigo -> Optional.empty(), repositorio,
            Clock.fixed(FECHA.plusSeconds(60), ZoneOffset.UTC));

        assertTrue(actualizar.ejecutar(CODIGO).isEmpty());
        assertEquals(aceptado, repositorio.buscar(CODIGO).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC", "", "１２３", "123\n", "123456789012345678901"})
    void elEsquemaTambienRechazaCodigosInvalidos(String codigo) {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
            INSERT INTO suministros (codigo, titular, verificado_en_utc) VALUES (?, ?, ?)
            """, codigo, "Titular de prueba", LocalDateTime.parse("2026-11-09T18:00:00")));
    }

    @Test
    void noPresentaUnFalloDeLecturaComoAusenciaDeDatos() {
        jdbc.execute("DROP TABLE suministros");

        assertThrows(RepositorioSuministrosNoDisponibleException.class,
            () -> repositorio.buscar(CODIGO));
    }

    @Test
    void informaUnFalloDeEscritura() {
        jdbc.execute("DROP TABLE suministros");

        assertThrows(RepositorioSuministrosNoDisponibleException.class,
            () -> repositorio.guardar(verificacion(CODIGO, "Titular", FECHA)));
    }

    private static SuministroVerificado verificacion(CodigoSuministro codigo,
                                                     String titular, Instant fecha) {
        return new SuministroVerificado(new Suministro(codigo, titular), fecha);
    }
}
