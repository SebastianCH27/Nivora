package pe.nivora.backend.lecturas.dominio.contratos;

public final class RepositorioSincronizacionNoDisponibleException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public RepositorioSincronizacionNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
