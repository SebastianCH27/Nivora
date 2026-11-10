package pe.nivora.backend.acceso.dominio.contratos;

public final class RepositorioAdministradoresNoDisponibleException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public RepositorioAdministradoresNoDisponibleException(Throwable causa) {
        super("No se pudo completar la operacion de cuentas administrativas.", causa);
    }
}
