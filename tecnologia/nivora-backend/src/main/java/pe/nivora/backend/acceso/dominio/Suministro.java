package pe.nivora.backend.acceso.dominio;

/**
 * Datos del suministro reconocido por la fuente de agua.
 * El titular puede ser distinto de la persona que realiza la consulta.
 */
public record Suministro(CodigoSuministro codigo, String titular) {

    public Suministro {
        if (codigo == null) {
            throw new IllegalArgumentException(
                "El suministro debe tener un codigo.");
        }

        if (titular == null || titular.isBlank()) {
            throw new IllegalArgumentException(
                "El suministro debe identificar a su titular.");
        }

        titular = titular.strip();
    }
}
