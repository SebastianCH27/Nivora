package pe.nivora.backend.lecturas.infraestructura.persistencia;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.nivora.backend.lecturas.dominio.EstadoRecuperacion;
import pe.nivora.backend.lecturas.dominio.LecturaAcumulada;
import pe.nivora.backend.lecturas.dominio.LoteLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.ProgresoRecuperacionModificadoException;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturasNoDisponibleException;
import pe.nivora.backend.lecturas.soporte.BaseDatosRecuperacion;

import static pe.nivora.backend.lecturas.soporte.BaseDatosRecuperacion.AHORA;
import static pe.nivora.backend.lecturas.soporte.BaseDatosRecuperacion.CODIGO;
import static pe.nivora.backend.lecturas.soporte.BaseDatosRecuperacion.pagina;
import static pe.nivora.backend.lecturas.soporte.BaseDatosRecuperacion.lectura;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositorioRecuperacionLecturasJdbcTests {
    private BaseDatosRecuperacion base;

    @BeforeEach
    void iniciar() { base = new BaseDatosRecuperacion(); }
    @AfterEach
    void cerrar() { base.close(); }

    @Test
    void confirmaLosDatosYElCursorYPermiteLeerlosDesdeOtraInstancia() {
        var inicial = base.progreso.consultar("empresa-agua", CODIGO);
        var paso = base.progreso.confirmar(inicial, pagina(true, 1, 2), AHORA);

        assertEquals(2, paso.incorporacion().nuevas());
        assertEquals(2, base.lecturas.contar(CODIGO));
        assertEquals("2", paso.progreso().cursor());
        assertEquals(EstadoRecuperacion.EN_CURSO, paso.progreso().estado());
        assertEquals(1, paso.progreso().revision());
        assertEquals(AHORA, paso.progreso().ultimaConfirmacion());
        assertEquals(paso.progreso(), base.nuevaInstanciaProgreso().consultar("empresa-agua", CODIGO));
    }

    @Test
    void unFalloAlGuardarElCursorRevierteLasLecturasIncorporadas() {
        base.jdbc.execute("ALTER TABLE progreso_recuperacion_lecturas ADD CONSTRAINT solo_cero CHECK (cursor_confirmado = '0')");
        var inicial = base.progreso.consultar("empresa-agua", CODIGO);

        assertThrows(RepositorioLecturasNoDisponibleException.class,
            () -> base.progreso.confirmar(inicial, pagina(true, 1, 2), AHORA));
        assertEquals(0, base.lecturas.contar(CODIGO));
        assertEquals(inicial, base.progreso.consultar("empresa-agua", CODIGO));
    }

    @Test
    void rechazaUnaConfirmacionQueUtilizaUnaRevisionAnterior() {
        var inicial = base.progreso.consultar("empresa-agua", CODIGO);
        var confirmado = base.progreso.confirmar(inicial, pagina(true, 1, 2), AHORA).progreso();

        assertThrows(ProgresoRecuperacionModificadoException.class,
            () -> base.progreso.confirmar(inicial, pagina(true, 1, 2), AHORA));
        assertEquals(confirmado, base.progreso.consultar("empresa-agua", CODIGO));
        assertEquals(2, base.lecturas.contar(CODIGO));
    }

    @Test
    void unFalloTardioNoSobrescribeUnAvanceMasReciente() {
        var inicial = base.progreso.consultar("empresa-agua", CODIGO);
        var confirmado = base.progreso.confirmar(inicial, pagina(true, 1, 2), AHORA).progreso();

        assertThrows(ProgresoRecuperacionModificadoException.class,
            () -> base.progreso.registrarFallo(inicial, EstadoRecuperacion.ERROR_FUENTE, AHORA));
        assertEquals(confirmado, base.progreso.consultar("empresa-agua", CODIGO));
        assertEquals(0, base.jdbc.queryForObject("SELECT COUNT(*) FROM incidencias_recuperacion_lecturas", Long.class));
    }

    @Test
    void unFalloDeAuditoriaRevierteTambienElEstadoDeError() {
        var inicial = base.progreso.consultar("empresa-agua", CODIGO);
        base.jdbc.execute("DROP TABLE incidencias_recuperacion_lecturas");

        assertThrows(RepositorioLecturasNoDisponibleException.class,
            () -> base.progreso.registrarFallo(inicial, EstadoRecuperacion.ERROR_FUENTE, AHORA));
        assertEquals(inicial, base.progreso.consultar("empresa-agua", CODIGO));
    }

    @Test
    void dosConfirmacionesSimultaneasSoloPermitenAvanzarUnaRevision() throws Exception {
        var inicial = base.progreso.consultar("empresa-agua", CODIGO);
        var inicio = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> tarea = () -> {
                inicio.await();
                try {
                    base.progreso.confirmar(inicial, pagina(true, 1, 2), AHORA);
                    return true;
                } catch (ProgresoRecuperacionModificadoException ex) { return false; }
            };
            var a = ejecutor.submit(tarea);
            var b = ejecutor.submit(tarea);
            inicio.countDown();
            int confirmaciones = (a.get(10, TimeUnit.SECONDS) ? 1 : 0)
                + (b.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, confirmaciones);
            assertEquals(2, base.lecturas.contar(CODIGO));
            assertEquals(1, base.progreso.consultar("empresa-agua", CODIGO).revision());
        }
    }

    @Test
    void unRechazoConservaLosDatosValidosPeroNoAdelantaElCursor() {
        base.lecturas.incorporar(pagina(false, 1), AHORA);
        var invalida = new LecturaAcumulada("empresa-agua", "2", CODIGO, "391",
            lectura(2).fechaMedicion(), 99);
        var lote = new LoteLecturas("empresa-agua", CODIGO, List.of(invalida, lectura(3)), "3", true);
        var inicial = base.progreso.consultar("empresa-agua", CODIGO);

        var paso = base.progreso.confirmar(inicial, lote, AHORA);

        assertEquals(1, paso.incorporacion().rechazadas());
        assertEquals(1, paso.incorporacion().nuevas());
        assertEquals("0", paso.progreso().cursor());
        assertEquals(EstadoRecuperacion.BLOQUEADO, paso.progreso().estado());
        assertEquals(2, base.lecturas.contar(CODIGO));
        assertEquals(1, base.lecturas.contarIncidencias(CODIGO));
    }

    @Test
    void unaPaginaVaciaNoPuedeAdelantarElCursor() {
        var inicial = base.progreso.consultar("empresa-agua", CODIGO);
        var lote = new LoteLecturas("empresa-agua", CODIGO, List.of(), "10", false);

        assertThrows(IllegalArgumentException.class, () -> base.progreso.confirmar(inicial, lote, AHORA));
        assertEquals(inicial, base.progreso.consultar("empresa-agua", CODIGO));
        assertTrue(base.lecturas.buscar("empresa-agua", "10").isEmpty());
    }
}
