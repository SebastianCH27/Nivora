package pe.nivora.backend.acceso.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.Suministro;
import pe.nivora.backend.acceso.dominio.SuministroVerificado;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministros;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministrosNoDisponibleException;
import pe.nivora.backend.acceso.dominio.contratos.RepositorioSuministros;
import pe.nivora.backend.acceso.dominio.contratos.RepositorioSuministrosNoDisponibleException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActualizarSuministroTests {

    private static final CodigoSuministro CODIGO = new CodigoSuministro("13100390");
    private static final Suministro SUMINISTRO = new Suministro(CODIGO, "Titular ficticio");
    private static final Instant FECHA = Instant.parse("2026-11-09T18:30:00.123456Z");
    private static final Clock RELOJ = Clock.fixed(FECHA, ZoneOffset.UTC);

    @Test
    void guardaElSuministroVerificadoConLaFechaDeLaConsulta() {
        var repositorio = new RepositorioControlado();
        FuenteSuministros fuente = codigo -> {
            assertEquals(CODIGO, codigo);
            return Optional.of(SUMINISTRO);
        };
        var caso = new ActualizarSuministro(fuente, repositorio, RELOJ);

        var resultado = caso.ejecutar(CODIGO).orElseThrow();

        assertEquals(new SuministroVerificado(SUMINISTRO, FECHA), resultado);
        assertEquals(resultado, repositorio.recibido);
        assertEquals(1, repositorio.escrituras);
    }

    @Test
    void unaAusenciaConfirmadaNoModificaElAlmacenamiento() {
        var repositorio = new RepositorioControlado();
        var caso = new ActualizarSuministro(codigo -> Optional.empty(), repositorio, RELOJ);

        assertTrue(caso.ejecutar(CODIGO).isEmpty());
        assertEquals(0, repositorio.escrituras);
        assertNull(repositorio.recibido);
    }

    @Test
    void unFalloExternoNoModificaLosDatosNiProduceAusencia() {
        var repositorio = new RepositorioControlado();
        var error = new FuenteSuministrosNoDisponibleException("Fuente temporalmente caida.");
        FuenteSuministros fuente = codigo -> { throw error; };
        var caso = new ActualizarSuministro(fuente, repositorio, RELOJ);

        assertSame(error, assertThrows(FuenteSuministrosNoDisponibleException.class,
            () -> caso.ejecutar(CODIGO)));
        assertEquals(0, repositorio.escrituras);
        assertNull(repositorio.recibido);
    }

    @Test
    void rechazaDatosDeUnCodigoDiferenteSinGuardarlos() {
        var repositorio = new RepositorioControlado();
        FuenteSuministros fuente = codigo -> Optional.of(
            new Suministro(new CodigoSuministro("99999999"), "Otro titular"));
        var caso = new ActualizarSuministro(fuente, repositorio, RELOJ);

        assertThrows(FuenteSuministrosNoDisponibleException.class,
            () -> caso.ejecutar(CODIGO));
        assertEquals(0, repositorio.escrituras);
        assertNull(repositorio.recibido);
    }

    @Test
    void noInformaExitoSiFallaLaPersistencia() {
        var repositorio = new RepositorioControlado();
        var error = new RepositorioSuministrosNoDisponibleException(
            "Base no disponible.", new IllegalStateException("Fallo simulado."));
        repositorio.errorAlGuardar = error;
        var caso = new ActualizarSuministro(codigo -> Optional.of(SUMINISTRO), repositorio, RELOJ);

        assertSame(error, assertThrows(RepositorioSuministrosNoDisponibleException.class,
            () -> caso.ejecutar(CODIGO)));
        assertEquals(1, repositorio.escrituras);
        assertNull(repositorio.recibido);
    }

    private static final class RepositorioControlado implements RepositorioSuministros {
        private SuministroVerificado recibido;
        private int escrituras;
        private RepositorioSuministrosNoDisponibleException errorAlGuardar;

        @Override
        public void guardar(SuministroVerificado verificado) {
            escrituras++;
            if (errorAlGuardar != null) {
                throw errorAlGuardar;
            }
            recibido = verificado;
        }

        @Override
        public Optional<SuministroVerificado> buscar(CodigoSuministro codigo) {
            throw new AssertionError("La actualizacion debe verificar la fuente externa.");
        }
    }
}
