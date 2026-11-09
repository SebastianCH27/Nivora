package pe.nivora.backend.acceso.dominio.contratos;

/** Una incidencia de la fuente no equivale a un suministro inexistente. */
public class FuenteSuministrosNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FuenteSuministrosNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public FuenteSuministrosNoDisponibleException(
            String mensaje, Throwable causa) {

        super(mensaje, causa);
    }
}
