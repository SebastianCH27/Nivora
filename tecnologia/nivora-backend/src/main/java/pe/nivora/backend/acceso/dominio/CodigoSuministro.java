package pe.nivora.backend.acceso.dominio;

/** Identificador del suministro; conserva sus posibles ceros iniciales. */
public record CodigoSuministro(String valor) {

    public CodigoSuministro {
        if (valor == null || !valor.matches("[0-9]{1,20}")) {
            throw new IllegalArgumentException(
                "El codigo de suministro debe contener entre 1 y 20 digitos.");
        }
    }
}
