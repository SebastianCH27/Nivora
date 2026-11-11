package pe.nivora.backend.acceso.dominio.contratos;

public final class CredencialesAdministrativasInvalidasException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public CredencialesAdministrativasInvalidasException() { super("Credenciales administrativas incorrectas."); }
}
