package pe.nivora.backend.lecturas.dominio.contratos;

public final class SupervisionLecturasNoDisponibleException extends RuntimeException {
    public SupervisionLecturasNoDisponibleException(Throwable causa) {
        super("No se pudo consultar la supervision de lecturas guardada.", causa);
    }
}
