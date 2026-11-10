package pe.nivora.backend.lecturas.aplicacion;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.nivora.backend.lecturas.dominio.EstadoRecuperacion;
import pe.nivora.backend.lecturas.dominio.LecturaAcumulada;
import pe.nivora.backend.lecturas.dominio.LoteLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturasNoDisponibleException;
import pe.nivora.backend.lecturas.soporte.BaseDatosRecuperacion;

import static pe.nivora.backend.lecturas.soporte.BaseDatosRecuperacion.AHORA;
import static pe.nivora.backend.lecturas.soporte.BaseDatosRecuperacion.CODIGO;
import static pe.nivora.backend.lecturas.soporte.BaseDatosRecuperacion.lectura;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecuperarLecturasTests {
    private BaseDatosRecuperacion base;
    private final List<String> cursores = new ArrayList<>();
    private final AtomicReference<List<LecturaAcumulada>> datos = new AtomicReference<>();

    @BeforeEach
    void iniciar() {
        base = new BaseDatosRecuperacion();
        datos.set(List.of(lectura(1), lectura(2), lectura(3), lectura(4), lectura(5), lectura(6)));
    }
    @AfterEach
    void cerrar() { base.close(); }

    @Test
    void limitaLasPaginasYUnaNuevaEjecucionReanudaDesdeElAvanceGuardado() {
        var primera = caso(fuente()).ejecutar(CODIGO, 2, 2);
        assertEquals(2, primera.paginas());
        assertEquals(4, primera.incorporacion().nuevas());
        assertEquals("4", primera.progreso().cursor());
        assertEquals(EstadoRecuperacion.EN_CURSO, primera.progreso().estado());

        var nuevoCaso = new RecuperarLecturas("empresa-agua", fuente(), base.nuevaInstanciaProgreso(),
            Clock.fixed(AHORA, ZoneOffset.UTC));
        var segunda = nuevoCaso.ejecutar(CODIGO, 2, 2);

        assertEquals(1, segunda.paginas());
        assertEquals(2, segunda.incorporacion().nuevas());
        assertEquals(EstadoRecuperacion.RECORRIDO_COMPLETO, segunda.progreso().estado());
        assertEquals(List.of("0", "2", "4"), cursores);
        assertEquals(6, base.lecturas.contar(CODIGO));
    }

    @Test
    void lasLecturasExistentesNoSeUsanComoCursorSiTodaviaNoHayProgreso() {
        base.lecturas.incorporar(BaseDatosRecuperacion.pagina(false, 1, 3), AHORA);

        var resultado = caso(fuente()).ejecutar(CODIGO, 2, 1);

        assertEquals(List.of("0"), cursores);
        assertEquals(1, resultado.incorporacion().nuevas());
        assertEquals(1, resultado.incorporacion().repetidas());
        assertEquals("2", resultado.progreso().cursor());
        assertTrue(base.lecturas.buscar("empresa-agua", "2").isPresent());
    }

    @Test
    void unFalloIntermedioConservaElCursorYElSiguienteIntentoContinuaDesdeEsePunto() {
        var falla = new AtomicBoolean(true);
        FuenteLecturas real = fuente();
        FuenteLecturas intermitente = (codigo, cursor, limite) -> {
            if (cursor.equals("2") && falla.get()) {
                throw new FuenteLecturasNoDisponibleException("Caida simulada.");
            }
            return real.consultar(codigo, cursor, limite);
        };
        assertThrows(FuenteLecturasNoDisponibleException.class,
            () -> caso(intermitente).ejecutar(CODIGO, 2, 10));
        var estado = base.progreso.consultar("empresa-agua", CODIGO);
        assertEquals("2", estado.cursor());
        assertEquals(EstadoRecuperacion.ERROR_FUENTE, estado.estado());
        assertEquals(AHORA, estado.ultimaConfirmacion());
        assertEquals(2, base.lecturas.contar(CODIGO));
        assertTrue(base.jdbc.queryForObject("SELECT abierta FROM incidencias_recuperacion_lecturas", Boolean.class));

        falla.set(false);
        var resultado = caso(intermitente).ejecutar(CODIGO, 2, 10);
        assertEquals(4, resultado.incorporacion().nuevas());
        assertEquals(0, resultado.incorporacion().repetidas());
        assertEquals(EstadoRecuperacion.RECORRIDO_COMPLETO, resultado.progreso().estado());
        assertFalse(base.jdbc.queryForObject("SELECT abierta FROM incidencias_recuperacion_lecturas", Boolean.class));
        assertEquals(1, base.jdbc.queryForObject("SELECT COUNT(*) FROM incidencias_recuperacion_lecturas", Long.class));
    }

    @Test
    void fallosRepetidosConservanUnaIncidenciaConSuContador() {
        FuenteLecturas caida = (codigo, cursor, limite) -> { throw new FuenteLecturasNoDisponibleException("Caida simulada."); };
        assertThrows(FuenteLecturasNoDisponibleException.class, () -> caso(caida).ejecutar(CODIGO, 2, 2));
        assertThrows(FuenteLecturasNoDisponibleException.class, () -> caso(caida).ejecutar(CODIGO, 2, 2));

        assertEquals("0", base.progreso.consultar("empresa-agua", CODIGO).cursor());
        assertEquals(1, base.jdbc.queryForObject("SELECT COUNT(*) FROM incidencias_recuperacion_lecturas", Long.class));
        assertEquals(2, base.jdbc.queryForObject("SELECT repeticiones FROM incidencias_recuperacion_lecturas", Long.class));
    }

    @Test
    void unaAusenciaConfirmadaQuedaDiferenciadaDelFalloDeComunicacion() {
        base.lecturas.incorporar(BaseDatosRecuperacion.pagina(false, 1), AHORA);
        var resultado = caso((codigo, cursor, limite) -> Optional.empty()).ejecutar(CODIGO, 2, 2);

        assertEquals(EstadoRecuperacion.SUMINISTRO_NO_ENCONTRADO, resultado.progreso().estado());
        assertEquals("0", resultado.progreso().cursor());
        assertEquals(0, resultado.paginas());
        assertEquals(1, base.lecturas.contar(CODIGO));
        assertEquals("SUMINISTRO_NO_ENCONTRADO", base.jdbc.queryForObject(
            "SELECT tipo FROM incidencias_recuperacion_lecturas", String.class));
    }

    @Test
    void unaPaginaVaciaTerminaElRecorridoConElMismoCursor() {
        datos.set(List.of());
        var resultado = caso(fuente()).ejecutar(CODIGO, 2, 2);
        assertEquals(1, resultado.paginas());
        assertEquals("0", resultado.progreso().cursor());
        assertEquals(EstadoRecuperacion.RECORRIDO_COMPLETO, resultado.progreso().estado());
        assertEquals(0, base.lecturas.contar(CODIGO));
    }

    @Test
    void unNuevoRecorridoRevisaElHistorialDesdeElInicioSinDuplicarlo() {
        var primera = caso(fuente()).ejecutar(CODIGO, 2, 10);
        var segunda = caso(fuente()).ejecutar(CODIGO, 2, 10);

        assertEquals(6, primera.incorporacion().nuevas());
        assertEquals(0, segunda.incorporacion().nuevas());
        assertEquals(6, segunda.incorporacion().repetidas());
        assertEquals(List.of("0", "2", "4", "0", "2", "4"), cursores);
        assertEquals(6, base.lecturas.contar(CODIGO));
    }

    @Test
    void elSiguienteRecorridoRecuperaUnIdAntiguoQueAparecioDespues() {
        datos.set(List.of(lectura(10), lectura(20), lectura(30), lectura(40)));
        caso(fuente()).ejecutar(CODIGO, 2, 10);
        datos.set(List.of(lectura(10), lectura(15), lectura(20), lectura(30), lectura(40)));

        var resultado = caso(fuente()).ejecutar(CODIGO, 2, 10);

        assertEquals(1, resultado.incorporacion().nuevas());
        assertEquals(4, resultado.incorporacion().repetidas());
        assertTrue(base.lecturas.buscar("empresa-agua", "15").isPresent());
        assertEquals(5, base.lecturas.contar(CODIGO));
    }

    @Test
    void unCambioHistoricoBloqueaElRecorridoYConservaLaLecturaAceptada() {
        caso(fuente()).ejecutar(CODIGO, 2, 10);
        var original = lectura(1);
        var cambiada = new LecturaAcumulada(original.fuente(), original.idLectura(), CODIGO,
            original.idMedidor(), original.fechaMedicion(), original.valorLitros() + 1);
        datos.set(List.of(cambiada, lectura(2), lectura(3), lectura(4), lectura(5), lectura(6)));

        var resultado = caso(fuente()).ejecutar(CODIGO, 2, 10);
        assertEquals(EstadoRecuperacion.BLOQUEADO, resultado.progreso().estado());
        assertEquals("0", resultado.progreso().cursor());
        assertEquals(1, resultado.incorporacion().rechazadas());
        assertEquals(original, base.lecturas.buscar("empresa-agua", "1").orElseThrow());

        int consultasAntes = cursores.size();
        assertEquals(0, caso(fuente()).ejecutar(CODIGO, 2, 10).paginas());
        assertEquals(consultasAntes, cursores.size());
        base.progreso.reiniciar(resultado.progreso(), AHORA);
        datos.set(List.of(lectura(1), lectura(2), lectura(3), lectura(4), lectura(5), lectura(6)));
        var reintentado = caso(fuente()).ejecutar(CODIGO, 2, 10);
        assertEquals(EstadoRecuperacion.RECORRIDO_COMPLETO, reintentado.progreso().estado());
        assertEquals(6, reintentado.incorporacion().repetidas());
        assertEquals(1, base.lecturas.contarIncidencias(CODIGO));
    }

    @Test
    void unLimiteDePaginasInvalidoNoConsultaLaFuente() {
        var llamadas = new AtomicInteger();
        FuenteLecturas fuente = (codigo, cursor, limite) -> { llamadas.incrementAndGet(); return Optional.empty(); };
        assertThrows(IllegalArgumentException.class, () -> caso(fuente).ejecutar(CODIGO, 2, 0));
        assertEquals(0, llamadas.get());
    }

    private RecuperarLecturas caso(FuenteLecturas fuente) {
        return new RecuperarLecturas("empresa-agua", fuente, base.progreso, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private FuenteLecturas fuente() {
        return (codigo, cursor, limite) -> {
            cursores.add(cursor);
            var pendientes = datos.get().stream()
                .filter(lectura -> Long.parseLong(lectura.idLectura()) > Long.parseLong(cursor))
                .sorted(Comparator.comparingLong(lectura -> Long.parseLong(lectura.idLectura()))).toList();
            var filas = pendientes.subList(0, Math.min(limite, pendientes.size()));
            return Optional.of(new LoteLecturas("empresa-agua", codigo, filas,
                filas.isEmpty() ? cursor : filas.getLast().idLectura(), pendientes.size() > limite));
        };
    }
}
