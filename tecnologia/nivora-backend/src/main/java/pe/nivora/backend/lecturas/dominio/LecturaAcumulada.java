package pe.nivora.backend.lecturas.dominio;

import java.time.Instant;
import java.util.Objects;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;

/** Contador acumulado de un medidor expresado en litros enteros. */
public record LecturaAcumulada(String fuente, String idLectura, CodigoSuministro suministro,
                              String idMedidor, Instant fechaMedicion, long valorLitros) {
    public LecturaAcumulada {
        validarIdentificador(fuente);
        validarIdentificador(idLectura);
        validarIdentificador(idMedidor);
        Objects.requireNonNull(suministro, "El suministro es obligatorio.");
        Objects.requireNonNull(fechaMedicion, "La fecha de medicion es obligatoria.");
        if (valorLitros < 0) {
            throw new IllegalArgumentException("El contador no puede ser negativo.");
        }
    }

    static void validarIdentificador(String valor) {
        if (valor == null || valor.isBlank() || !valor.equals(valor.strip()) || valor.length() > 100) {
            throw new IllegalArgumentException("El identificador debe contener entre 1 y 100 caracteres sin espacios exteriores.");
        }
    }
}
