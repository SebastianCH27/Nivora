package pe.nivora.backend.acceso.dominio.contratos;

public final class PermisoGestionAdministrativaException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public PermisoGestionAdministrativaException() { super("Se requiere permiso para gestionar administradores."); }
}
