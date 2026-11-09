package pe.nivora.backend.acceso.dominio;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Fecha de verificacion externa, con precision de microsegundos; no es una lectura. */
public record SuministroVerificado(Suministro suministro, Instant fechaVerificacion) {

    public SuministroVerificado {
        Objects.requireNonNull(suministro, "El suministro es obligatorio.");
        Objects.requireNonNull(fechaVerificacion, "La fecha de verificacion es obligatoria.");
        fechaVerificacion = fechaVerificacion.truncatedTo(ChronoUnit.MICROS);
    }
}
