package pe.nivora.backend.acceso.dominio;

/** Datos asociados al documento segun la fuente de identidad. */
public record IdentidadConsultada(Dni dni, String nombreCompleto) {

    public IdentidadConsultada {
        if (dni == null || nombreCompleto == null || nombreCompleto.isBlank()
                || nombreCompleto.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Los datos de identidad no son utilizables.");
        }
        nombreCompleto = nombreCompleto.strip();
    }

    @Override
    public String toString() {
        return "IdentidadConsultada[datos ocultos]";
    }
}
