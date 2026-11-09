package pe.nivora.backend.lecturas.dominio.contratos;

public class FuenteLecturasNoDisponibleException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public FuenteLecturasNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public FuenteLecturasNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
