package pe.nivora.backend.lecturas.dominio;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;

public record ProgresoRecuperacion(String fuente, CodigoSuministro suministro, String cursor,
                                  long revision, EstadoRecuperacion estado,
                                  Instant ultimaConfirmacion, Instant actualizadoEn) {
    public ProgresoRecuperacion {
        LecturaAcumulada.validarIdentificador(fuente);
        LecturaAcumulada.validarIdentificador(cursor);
        Objects.requireNonNull(suministro);
        Objects.requireNonNull(estado);
        if (revision < 0) { throw new IllegalArgumentException("La revision no puede ser negativa."); }
    }

    public static ProgresoRecuperacion inicial(String fuente, CodigoSuministro suministro) {
        return new ProgresoRecuperacion(fuente, suministro, "0", 0,
            EstadoRecuperacion.PENDIENTE, null, null);
    }

    public void comprobarPagina(LoteLecturas lote) {
        if (!fuente.equals(lote.fuente()) || !suministro.equals(lote.suministro())) {
            throw new IllegalArgumentException("La pagina no corresponde al progreso solicitado.");
        }
        if (estado == EstadoRecuperacion.BLOQUEADO || estado == EstadoRecuperacion.RECORRIDO_COMPLETO) {
            throw new IllegalStateException("El recorrido debe reiniciarse antes de confirmar otra pagina.");
        }
        if (!lote.lecturas().isEmpty() && cursor.equals(lote.siguienteCursor())) {
            throw new IllegalArgumentException("La pagina no permite avanzar el cursor.");
        }
        if (lote.lecturas().isEmpty() && !cursor.equals(lote.siguienteCursor())) {
            throw new IllegalArgumentException("Una pagina vacia debe conservar el cursor solicitado.");
        }
    }

    public ProgresoRecuperacion confirmar(LoteLecturas lote, ResultadoIncorporacion resultado, Instant ahora) {
        comprobarPagina(lote);
        boolean rechazo = resultado.rechazadas() > 0;
        var siguienteEstado = rechazo ? EstadoRecuperacion.BLOQUEADO
            : lote.hayMas() ? EstadoRecuperacion.EN_CURSO : EstadoRecuperacion.RECORRIDO_COMPLETO;
        Instant fecha = ahora.truncatedTo(ChronoUnit.MICROS);
        return new ProgresoRecuperacion(fuente, suministro, rechazo ? cursor : lote.siguienteCursor(),
            Math.incrementExact(revision), siguienteEstado, rechazo ? ultimaConfirmacion : fecha, fecha);
    }

    public ProgresoRecuperacion registrarFallo(EstadoRecuperacion fallo, Instant ahora) {
        if (fallo != EstadoRecuperacion.ERROR_FUENTE && fallo != EstadoRecuperacion.SUMINISTRO_NO_ENCONTRADO) {
            throw new IllegalArgumentException("El tipo de fallo no corresponde a la fuente.");
        }
        if (estado == EstadoRecuperacion.BLOQUEADO || estado == EstadoRecuperacion.RECORRIDO_COMPLETO) {
            throw new IllegalStateException("Ese estado no admite una consulta externa pendiente.");
        }
        return new ProgresoRecuperacion(fuente, suministro, cursor, Math.incrementExact(revision),
            fallo, ultimaConfirmacion, ahora.truncatedTo(ChronoUnit.MICROS));
    }

    public ProgresoRecuperacion reiniciar(Instant ahora) {
        return new ProgresoRecuperacion(fuente, suministro, "0", Math.incrementExact(revision),
            EstadoRecuperacion.PENDIENTE, ultimaConfirmacion, ahora.truncatedTo(ChronoUnit.MICROS));
    }
}
