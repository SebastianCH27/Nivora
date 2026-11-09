package pe.nivora.backend.lecturas.dominio.contratos;

public class RepositorioLecturasNoDisponibleException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RepositorioLecturasNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
