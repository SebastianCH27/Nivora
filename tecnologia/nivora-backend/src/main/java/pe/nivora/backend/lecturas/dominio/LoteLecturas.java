package pe.nivora.backend.lecturas.dominio;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;

/** Una pagina recibida; aun no acredita incorporacion ni confirmacion en la base propia. */
public record LoteLecturas(String fuente, CodigoSuministro suministro,
                          List<LecturaAcumulada> lecturas, String siguienteCursor, boolean hayMas) {
    public LoteLecturas {
        LecturaAcumulada.validarIdentificador(fuente);
        LecturaAcumulada.validarIdentificador(siguienteCursor);
        Objects.requireNonNull(suministro, "El suministro es obligatorio.");
        lecturas = List.copyOf(lecturas);
        var ids = new HashSet<String>();
        for (var lectura : lecturas) {
            if (!fuente.equals(lectura.fuente()) || !suministro.equals(lectura.suministro())
                    || !ids.add(lectura.idLectura())) {
                throw new IllegalArgumentException("El lote mezcla fuentes, suministros o identificadores repetidos.");
            }
        }
        if (hayMas && lecturas.isEmpty()) {
            throw new IllegalArgumentException("Una pagina vacia no puede anunciar mas lecturas.");
        }
    }
}
