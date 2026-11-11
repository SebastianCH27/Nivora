package pe.nivora.backend.acceso.dominio.contratos;

public final class AdministradorNoEncontradoException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public AdministradorNoEncontradoException() { super("No existe una cuenta administrativa con ese usuario."); }
}
