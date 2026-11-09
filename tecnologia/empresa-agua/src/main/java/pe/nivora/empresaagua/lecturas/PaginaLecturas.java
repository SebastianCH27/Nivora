package pe.nivora.empresaagua.lecturas;

import java.time.Instant;
import java.util.List;

/** Lecturas acumuladas persistidas; los identificadores se transmiten como texto. */
public record PaginaLecturas(
    String fuente,
    String codigoSuministro,
    String tipoMedicion,
    String unidad,
    List<Lectura> lecturas,
    String siguienteDespuesDe,
    boolean hayMas
) {
    public PaginaLecturas {
        lecturas = List.copyOf(lecturas);
    }

    public record Lectura(
        String idLectura,
        String idMedidor,
        Instant fechaMedicion,
        long valorLitros
    ) {}
}
