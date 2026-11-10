package pe.nivora.backend.lecturas.dominio;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;

public record ReservaSincronizacion(String fuente, CodigoSuministro suministro,
        String token, Instant venceEn, int fallosConsecutivos) {
    public ReservaSincronizacion {
        LecturaAcumulada.validarIdentificador(fuente);
        Objects.requireNonNull(suministro);
        Objects.requireNonNull(venceEn);
        UUID.fromString(token);
        if (fallosConsecutivos < 0) throw new IllegalArgumentException("Los fallos no pueden ser negativos.");
    }
}
