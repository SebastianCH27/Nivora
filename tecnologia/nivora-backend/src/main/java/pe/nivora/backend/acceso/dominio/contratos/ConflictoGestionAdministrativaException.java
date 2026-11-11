package pe.nivora.backend.acceso.dominio.contratos;

public final class ConflictoGestionAdministrativaException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public ConflictoGestionAdministrativaException(String mensaje) { super(mensaje); }
}
