package pe.nivora.backend.acceso.dominio.contratos;

public final class SesionesAdministrativasNoDisponiblesException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public SesionesAdministrativasNoDisponiblesException(Throwable causa) {
        super("No se pudo completar la operacion de sesiones administrativas.", causa);
    }
}
