package pe.nivora.backend.acceso.dominio;

/** Numero de DNI, con sus posibles ceros iniciales. */
public record Dni(String valor) {

    public Dni {
        if (valor == null || !valor.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("El DNI debe contener exactamente 8 digitos.");
        }
    }

    @Override
    public String toString() {
        return "Dni[oculto]";
    }
}
