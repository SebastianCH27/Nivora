package pe.nivora.backend.lecturas.dominio;

import java.time.Instant;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;

/** Instantanea de consulta; no representa consumo ni modifica la recuperacion. */
public record SupervisionLecturas(String fuente, CodigoSuministro suministro,
        Instant consultadoEn, Almacenamiento almacenamiento,
        Recuperacion recuperacion, Programacion programacion) {

    public record Almacenamiento(long lecturasGuardadas, Instant primeraMedicion,
            Instant ultimaMedicion, Instant ultimaRecepcion,
            long incidenciasLecturas, long incidenciasRecuperacionAbiertas) {}

    public record Recuperacion(EstadoRecuperacion estado, String cursorConfirmado,
            String revision, Instant ultimaConfirmacion, Instant actualizadoEn,
            Corte corte) {}

    public record Corte(String hastaIdLectura, Instant confirmadoHasta,
            Instant capturadoEn) {}

    public record Programacion(EstadoSincronizacion ultimoResultado,
            int fallosConsecutivos, Instant ultimoIntento, Instant ultimoExito,
            Instant proximoIntento, Instant reservaHasta,
            boolean reservaVigente, boolean reservaVencida) {}
}
