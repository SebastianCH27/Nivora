package pe.nivora.empresaagua.lecturas;

import java.time.Instant;

/** Limite de altas confirmadas bajo el control del productor. */
public record CorteLecturas(
    String fuente, String hastaIdLectura, Instant confirmadoHasta) {
}
