package pe.nivora.backend.lecturas.dominio;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.OptionalLong;

public final class PoliticaIncorporacionLecturas {
    private PoliticaIncorporacionLecturas() {}

    public static Optional<MotivoRechazoLectura> validarFecha(LecturaAcumulada lectura,
                                                             Instant recibidaEn) {
        Instant fecha = lectura.fechaMedicion();
        try {
            int anio = fecha.atOffset(ZoneOffset.UTC).getYear();
            if (anio < 1000 || anio > 9999) {
                return Optional.of(MotivoRechazoLectura.FECHA_FUERA_DE_RANGO);
            }
        } catch (DateTimeException ex) {
            return Optional.of(MotivoRechazoLectura.FECHA_FUERA_DE_RANGO);
        }
        if (!fecha.equals(fecha.truncatedTo(ChronoUnit.MICROS))) {
            return Optional.of(MotivoRechazoLectura.FECHA_PRECISION_NO_SOPORTADA);
        }
        if (fecha.isAfter(recibidaEn)) {
            return Optional.of(MotivoRechazoLectura.FECHA_POSTERIOR_A_RECEPCION);
        }
        return Optional.empty();
    }

    public static Optional<MotivoRechazoLectura> validarContador(LecturaAcumulada lectura,
            OptionalLong anterior, OptionalLong posterior) {
        if ((anterior.isPresent() && lectura.valorLitros() < anterior.getAsLong())
                || (posterior.isPresent() && lectura.valorLitros() > posterior.getAsLong())) {
            return Optional.of(MotivoRechazoLectura.CONTADOR_INCONSISTENTE);
        }
        return Optional.empty();
    }
}
