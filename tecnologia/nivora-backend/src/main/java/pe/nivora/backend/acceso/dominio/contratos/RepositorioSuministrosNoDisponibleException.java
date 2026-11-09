package pe.nivora.backend.acceso.dominio.contratos;

/** Una incidencia de persistencia no debe interpretarse como ausencia de datos. */
public class RepositorioSuministrosNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RepositorioSuministrosNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
