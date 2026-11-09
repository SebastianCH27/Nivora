package pe.nivora.backend.lecturas.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.LecturaAcumulada;
import pe.nivora.backend.lecturas.dominio.LoteLecturas;
import pe.nivora.backend.lecturas.dominio.ResultadoIncorporacion;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturasNoDisponibleException;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturasNoDisponibleException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportarPaginaLecturasTests {
    private static final CodigoSuministro CODIGO = new CodigoSuministro("13100390");
    private static final Instant FECHA = Instant.parse("2026-11-09T20:00:00Z");
    private static final Clock RELOJ = Clock.fixed(FECHA, ZoneOffset.UTC);

    @Test
    void consultaUnaPaginaYEntregaSuLoteYHoraAlRepositorio() {
        var esperado = new LoteLecturas("empresa-agua", CODIGO, List.of(
            new LecturaAcumulada("empresa-agua", "391", CODIGO, "391", FECHA.minusSeconds(60), 100)),
            "391", false);
        var repositorio = new RepositorioControlado();
        FuenteLecturas fuente = (codigo, cursor, limite) -> {
            assertEquals(CODIGO, codigo);
            assertEquals("0", cursor);
            assertEquals(2, limite);
            return Optional.of(esperado);
        };

        var resultado = new ImportarPaginaLecturas(fuente, repositorio, RELOJ)
            .ejecutar(CODIGO, "0", 2).orElseThrow();

        assertSame(esperado, repositorio.recibido);
        assertEquals(FECHA, repositorio.fecha);
        assertEquals(new ResultadoIncorporacion(1, 0, 0), resultado.incorporacion());
        assertEquals("391", resultado.siguienteCursor());
    }

    @Test
    void unaAusenciaConfirmadaNoTocaElAlmacenamiento() {
        var repositorio = new RepositorioControlado();
        var caso = new ImportarPaginaLecturas((codigo, cursor, limite) -> Optional.empty(), repositorio, RELOJ);
        assertTrue(caso.ejecutar(CODIGO, "0", 2).isEmpty());
        assertEquals(0, repositorio.llamadas);
    }

    @Test
    void unFalloDeFuenteNoSePresentaComoAusenciaNiEscritura() {
        var repositorio = new RepositorioControlado();
        var error = new FuenteLecturasNoDisponibleException("Caida simulada.");
        FuenteLecturas fuente = (codigo, cursor, limite) -> { throw error; };
        var caso = new ImportarPaginaLecturas(fuente, repositorio, RELOJ);
        assertSame(error, assertThrows(FuenteLecturasNoDisponibleException.class,
            () -> caso.ejecutar(CODIGO, "0", 2)));
        assertEquals(0, repositorio.llamadas);
    }

    @Test
    void unFalloDePersistenciaImpideDevolverUnResultadoExitoso() {
        var repositorio = new RepositorioControlado();
        var error = new RepositorioLecturasNoDisponibleException("Fallo simulado.", new IllegalStateException());
        repositorio.error = error;
        var lote = new LoteLecturas("empresa-agua", CODIGO, List.of(), "0", false);
        var caso = new ImportarPaginaLecturas((codigo, cursor, limite) -> Optional.of(lote), repositorio, RELOJ);
        assertSame(error, assertThrows(RepositorioLecturasNoDisponibleException.class,
            () -> caso.ejecutar(CODIGO, "0", 2)));
    }

    @Test
    void unLoteDeOtroSuministroNoSeIncorpora() {
        var repositorio = new RepositorioControlado();
        var lote = new LoteLecturas("empresa-agua", new CodigoSuministro("13100391"), List.of(), "0", false);
        var caso = new ImportarPaginaLecturas((codigo, cursor, limite) -> Optional.of(lote), repositorio, RELOJ);
        assertThrows(IllegalArgumentException.class, () -> caso.ejecutar(CODIGO, "0", 2));
        assertEquals(0, repositorio.llamadas);
    }

    private static final class RepositorioControlado implements RepositorioLecturas {
        private LoteLecturas recibido;
        private Instant fecha;
        private int llamadas;
        private RepositorioLecturasNoDisponibleException error;

        @Override
        public ResultadoIncorporacion incorporar(LoteLecturas lote, Instant recibidaEn) {
            llamadas++;
            if (error != null) { throw error; }
            recibido = lote;
            fecha = recibidaEn;
            return new ResultadoIncorporacion(1, 0, 0);
        }

        @Override
        public Optional<LecturaAcumulada> buscar(String fuente, String id) { throw new AssertionError(); }
        @Override
        public long contar(CodigoSuministro codigo) { throw new AssertionError(); }
        @Override
        public long contarIncidencias(CodigoSuministro codigo) { throw new AssertionError(); }
    }
}
